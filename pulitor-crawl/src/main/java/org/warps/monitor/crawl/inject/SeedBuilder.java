package org.warps.monitor.crawl.inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.monitor.common.options.CrawlOptions;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.pulsar.persist.WebPage;
import org.warps.pulsar.persist.metadata.Mark;

import java.time.Instant;

import static org.warps.pulsar.common.PulsarConstants.YES_STRING;

/**
 * Created by vincent on 16-9-24.
 * Copyright @ 2013-2016 Warpspeed Information. All rights reserved
 */
public class SeedBuilder {

  public static final Logger LOG = LoggerFactory.getLogger(SeedBuilder.class);

  private Instant impreciseNow = Instant.now();
  private PulsarConfiguration conf;

  public SeedBuilder(PulsarConfiguration conf) {
    this.conf = conf;
  }

  public WebPage create(String configuredUrl) {
    configuredUrl = StringUtils.stripToEmpty(configuredUrl);
    String url = StringUtils.substringBefore(configuredUrl, " ");
    if (url.isEmpty()) {
      return null;
    }

    WebPage page = WebPage.newWebPage(url);
    page.setCrawlOpts(configuredUrl);

    CrawlOptions crawlOptions = CrawlOptions.parse(configuredUrl, conf);
    return build(url, crawlOptions, page);
  }

  private WebPage build(String url, CrawlOptions crawlOptions, WebPage page) {
    if (!page.hasLegalUrl()) {
      LOG.warn("Ignore malformed url : " + url);
      return null;
    }

    // page.setScore(opts.getScore());
    page.setScore(0);
    page.setDistance(0);
    page.setCreateTime(impreciseNow);
    page.markAsSeed();
    page.setCrawlOpts(crawlOptions.toString());

    page.setFetchCount(0);
    page.setFetchTime(impreciseNow);
    page.setFetchInterval(crawlOptions.getFetchInterval());
    page.setFetchPriority(crawlOptions.getFetchPriority());

    page.putMark(Mark.INJECT, YES_STRING);

    return page;
  }
}
