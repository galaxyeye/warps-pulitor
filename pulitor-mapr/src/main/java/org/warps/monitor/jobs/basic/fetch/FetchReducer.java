/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.warps.monitor.jobs.basic.fetch;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.IntWritable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.warps.monitor.common.CommonCounter;
import org.warps.monitor.common.EncodingDetector;
import org.warps.monitor.common.PulsarCounters;
import org.warps.monitor.common.options.CrawlOptions;
import org.warps.monitor.crawl.fetch.FetchEntry;
import org.warps.monitor.crawl.index.GeneralIndexingFilter;
import org.warps.monitor.crawl.index.IndexDocument;
import org.warps.monitor.crawl.index.IndexerMapping;
import org.warps.monitor.crawl.index.SolrIndexWriter;
import org.warps.monitor.crawl.parse.OutlinkFilter;
import org.warps.monitor.crawl.parse.Parser;
import org.warps.monitor.crawl.parse.html.BoilerpipeFilter;
import org.warps.monitor.crawl.protocol.Content;
import org.warps.monitor.crawl.protocol.ProtocolOutput;
import org.warps.monitor.crawl.protocol.http.Http;
import org.warps.monitor.jobs.core.GoraReducer;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.StringUtil;
import org.warps.pulsar.common.UrlUtil;
import org.warps.pulsar.persist.CrawlStatus;
import org.warps.pulsar.persist.Outlink;
import org.warps.pulsar.persist.ProtocolStatus;
import org.warps.pulsar.persist.WebPage;
import org.warps.pulsar.persist.gora.generated.GoraWebPage;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.warps.pulsar.common.PulsarConstants.YES_STRING;
import static org.warps.pulsar.persist.metadata.Mark.*;

public class FetchReducer extends GoraReducer<IntWritable, FetchEntry, String, GoraWebPage> {

  private enum Counter { rFetched, rParsed, rIndexed }
  static { PulsarCounters.register(Counter.class); }

  private Instant impreciseNow = Instant.now();
  private BoilerpipeFilter boilerpipeFilter;
  private IndexerMapping indexerMapping;
  private SolrIndexWriter indexWriter;
  private OutlinkFilter outlinkFilter;
  private Duration defaultInterval = Duration.ofMinutes(20);

  @Override
  public void setup(Context context) {
    boilerpipeFilter = new BoilerpipeFilter(conf);
    indexerMapping = new IndexerMapping(conf);
    indexWriter = new SolrIndexWriter(indexerMapping, conf);
    indexWriter.open(conf);
    outlinkFilter = new OutlinkFilter(conf);

    LOG.info(Params.of(
        "className", getClass()
        ).merge(
        outlinkFilter.getParams(),
        indexWriter.getParams()
    ).format());
  }

  @Override
  protected void reduce(IntWritable key, Iterable<FetchEntry> entries, Context context) throws IOException, InterruptedException {
    pulsarCounters.increase(CommonCounter.rRows);

    for (FetchEntry entry : entries) {
      String url = UrlUtil.unreverseUrl(entry.getReservedUrl());
      WebPage page = entry.getWebPage();

      int code = process(url, page);
      if (code < 0) {
        LOG.warn("Failed to fetch: " + url + "\tcode: " + code);
        int fetchCount = page.getFetchCount();
        page.setFetchTime(page.getFetchTime().plusSeconds(fetchCount * 24 * 3600));
        if (fetchCount > 5) {
          page.putMark(INACTIVE, YES_STRING);
        }
      }
    }
  }

  private int process(String url, WebPage page) throws IOException {
    page = fetchContent(url, page, conf);
    if (page == null) {
      return -1;
    }

    if (!page.hasMark(FETCH)) {
      return -2;
    }

    pulsarCounters.increase(Counter.rFetched);
    EncodingDetector encodingDetector = new EncodingDetector(conf);
    String encoding = encodingDetector.sniffEncoding(page);
    InputSource inputSource = Parser.getContentAsInputSource(page, encoding);
    Document doc = Jsoup.parse(inputSource.getByteStream(), encoding, page.getBaseUrl());

    List<Outlink> outlinks = new ArrayList<>();
    if (page.isSeed()) {
      Elements links = doc.select("a");
      for (Element link : links) {
        Outlink outlink = new Outlink(link.getAttr("href"), link.text());
        if (outlinkFilter.asPredicate(page).test(outlink)) {
          outlinks.add(outlink);
        }
        // LOG.info(outlinkFilter.getFilterReport().stream().collect(Collectors.joining("\n")));
      }
      LOG.info("Found " + outlinks.size() + " outlinks out of total " + links.size() + " unfiltered ones in " + url);
      page.addToOldOutlinks(outlinks);
    }

    // Extract fields for news
    boilerpipeFilter.extract(page, page.getEncoding());

    GeneralIndexingFilter indexingFilter = new GeneralIndexingFilter(conf);
    IndexDocument indexDocument = new IndexDocument(page.reversedUrl());
    indexDocument = indexingFilter.filter(indexDocument, url, page);

    if (indexDocument != null) {
      indexWriter.write(indexDocument);
      pulsarCounters.increase(Counter.rIndexed);
    }

    try {
      CrawlOptions crawlOptions = CrawlOptions.parse(page.getCrawlOpts(), conf);
      page.setFetchTime(startTime.plus(crawlOptions.getFetchInterval()));
      context.write(page.reversedUrl(), page.get());
      pulsarCounters.increase(CommonCounter.rPersist);

      if (page.isSeed()) {
        for (Outlink outlink : outlinks) {
          WebPage outgoingPage = createNewRow(outlink.getToUrl());
          context.write(UrlUtil.reverseUrl(outlink.getToUrl()), outgoingPage.get());
          // LOG.debug("Created new page " + outlink.getToUrl());
          pulsarCounters.increase(CommonCounter.rOutlinks);
        }
      }
      else {
        page.setFetchTime(startTime.plus(Duration.ofDays(3650)));
        page.putMark(INACTIVE, YES_STRING);
      }
    } catch (IOException|InterruptedException e) {
      LOG.error(StringUtil.stringifyException(e));
      return -100;
    }

    return 0;
  }

  public WebPage fetchContent(String url, WebPage page, Configuration conf) {
    Http http = new Http(conf);

    ProtocolOutput protocolOutput = http.getProtocolOutput(url, page);
    ProtocolStatus pstatus = protocolOutput.getStatus();

    if (!ProtocolStatus.isSuccess(pstatus)) {
      LOG.error("Fetch failed with protocol status, "
          + ProtocolStatus.getName(pstatus.getCode())
          + " : " + ProtocolStatus.getMessage(pstatus));
      return null;
    }

    Content content = protocolOutput.getContent();

    page.setStatus((int) CrawlStatus.STATUS_FETCHED);
    page.setProtocolStatus(pstatus);
    page.increaseFetchCount();
    page.setBaseUrl(content.getBaseUrl());
    page.setContent(content.getContent());
    updateFetchTime(page, impreciseNow);

    page.putMarkIfNonNull(FETCH, page.getMark(GENERATE));
    page.removeMark(INJECT);
    page.removeMark(GENERATE);

    return page;
  }

  public void updateFetchTime(WebPage page, Instant newFetchTime) {
    Instant lastFetchTime = page.getFetchTime();

    if (lastFetchTime.isBefore(newFetchTime)) {
      page.setPrevFetchTime(lastFetchTime);
    }
    page.setFetchTime(newFetchTime);

    page.putFetchTimeHistory(newFetchTime);
  }

  private WebPage createNewRow(String url) {
    WebPage page = WebPage.newWebPage(url);

    page.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
    page.setCreateTime(impreciseNow);
    page.setScore(0);
    page.setFetchCount(0);

    page.setFetchTime(impreciseNow);
    page.setFetchInterval(defaultInterval);
    page.setRetriesSinceFetch(0);

    return page;
  }
}
