/**
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
 */
package org.warps.monitor.crawl.parse.html;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.DocumentFragment;
import org.warps.monitor.crawl.parse.HTMLMetaTags;
import org.warps.monitor.crawl.parse.ParseResult;
import org.warps.monitor.crawl.parse.Parser;
import org.warps.pulsar.persist.PageCategory;
import org.warps.pulsar.persist.ParseStatus;
import org.warps.pulsar.persist.WebPage;
import org.warps.pulsar.boilerpipe.document.TextDocument;
import org.warps.pulsar.boilerpipe.extractors.ChineseNewsExtractor;
import org.warps.pulsar.boilerpipe.sax.SAXInput;
import org.warps.pulsar.boilerpipe.utils.ProcessingException;
import org.warps.pulsar.common.Parameterized;
import org.warps.pulsar.common.PulsarConfiguration;
import org.xml.sax.InputSource;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parse html document into fields
 */
public class BoilerpipeFilter implements Parameterized {

  private static final Log LOG = LogFactory.getLog(BoilerpipeFilter.class.getName());

  private PulsarConfiguration conf;

  public static final String PARSE_META_PREFIX = "meta_";

  private Set<String> metatagset = new HashSet<>();

  public BoilerpipeFilter(PulsarConfiguration conf) {
    reload(conf);
  }

  public void reload(PulsarConfiguration conf) {
    this.conf = conf;
    // specify whether we want a specific subset of metadata
    // by default take everything we can find
    String[] values = conf.getStrings("metatags.names", "*");
    for (String val : values) {
      metatagset.add(val.toLowerCase(Locale.ROOT));
    }
  }

  public PulsarConfiguration getConf() { return this.conf; }

  public ParseResult filter(String url, WebPage page, ParseResult parseResult, HTMLMetaTags metaTags, DocumentFragment doc) {
    String encoding = page.getEncoding();
    if (!metaTags.getNoIndex()) {
      extract(page, encoding);
    }

    parseResult.setParseStatus(ParseStatus.STATUS_SUCCESS);
    return parseResult;
  }

  /**
   * Extract the page into fields
   * */
  public TextDocument extract(WebPage page, String encoding) {
    // Get input source, again. InputSource is not reusable
    TextDocument doc = extract(page, Parser.getContentAsInputSource(page, encoding));

    if (doc != null) {
      page.setContentTitle(doc.getContentTitle());
      page.setContentText(doc.getTextContent());
      page.setContentTextLength(doc.getTextContent().length());
      page.setPageCategory(PageCategory.valueOf(doc.getPageCategoryAsString()));
      page.updateContentPublishTime(doc.getPublishTime());
      page.updateContentModifiedTime(doc.getModifiedTime());

      page.getPageEntity().getOrCreate(this.getClass()).setFields(doc.getFields());
    }

    return doc;
  }

  private TextDocument extract(WebPage page, InputSource input) {
    if (page.getContent() == null) {
      LOG.warn("Can not extract page with null content, url : " + page.url());
      return null;
    }

    try {
      TextDocument doc = new SAXInput().parse(page.url(), input);
      ChineseNewsExtractor extractor = new ChineseNewsExtractor();
      extractor.process(doc);

      return doc;
    } catch (ProcessingException e) {
      LOG.warn("Failed to extract text content by boilerpipe, " + e.getMessage());
    }

    return null;
  }
}
