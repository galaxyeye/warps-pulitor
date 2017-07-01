package org.warps.monitor.crawl.parse;

import org.warps.monitor.common.URLUtil;
import org.warps.monitor.common.options.CrawlOptions;
import org.warps.pulsar.persist.Outlink;
import org.warps.pulsar.persist.WebPage;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.pulsar.common.ReloadableParameterized;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import static org.warps.pulsar.common.PulsarConfiguration.*;

/**
 * Created by vincent on 17-5-21.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
// TODO : Move to a plugin
public class OutlinkFilter implements ReloadableParameterized {
  private URLUtil.GroupMode groupMode;
  private boolean ignoreExternalLinks;

  private int maxUrlLength;

  private PulsarConfiguration conf;
  private String sourceHost;
  private CrawlOptions crawlOptions;
  private Set<String> oldOutlinks = new TreeSet<>();
  private List<String> filterReport = new LinkedList<>();

  public OutlinkFilter(PulsarConfiguration conf) {
    reload(conf);
  }

  @Override
  public PulsarConfiguration getConf() {
    return conf;
  }

  @Override
  public Params getParams() {
    return Params.of(
        "groupMode", groupMode,
        "ignoreExternalLinks", ignoreExternalLinks,
        "maxUrlLength", maxUrlLength,
        "defaultAnchorLenMin", conf.get(PARSE_MIN_ANCHOR_LENGTH),
        "defaultAnchorLenMax", conf.get(PARSE_MAX_ANCHOR_LENGTH)
    );
  }

  @Override
  public void reload(PulsarConfiguration conf) {
    this.conf = conf;

    groupMode = conf.getEnum(FETCH_QUEUE_MODE, URLUtil.GroupMode.BY_HOST);
    ignoreExternalLinks = conf.getBoolean(PARSE_IGNORE_EXTERNAL_LINKS, false);
    maxUrlLength = conf.getInt(PARSE_MAX_URL_LENGTH, 1024);

    LOG.info(getParams().formatAsLine());
  }

  public void reset(WebPage page) {
    crawlOptions = CrawlOptions.parse(page.getCrawlOpts(), conf);
    sourceHost = ignoreExternalLinks ? URLUtil.getHost(page.url(), groupMode) : "";
    oldOutlinks.clear();
    filterReport.clear();
    page.getOldOutlinks().forEach(ol -> oldOutlinks.add(ol.toString()));
  }

  public List<String> getFilterReport() {
    return filterReport;
  }

  public Predicate<Outlink> asPredicate(WebPage page) {
    reset(page);
    filterReport.add(crawlOptions.toString());
    return ol -> {
      int r = this.filter(ol);
      filterReport.add(r + " <- " + ol.getToUrl() + "\t" + ol.getAnchor());
      return 0 == r;
    };
  }

  public int filter(Outlink ol) {
    String toUrl = ol.getToUrl();

    if (toUrl.isEmpty()) {
      return 101;
    }

    long urlLength = ol.getToUrl().length();
    if (urlLength < crawlOptions.getMinU() || urlLength > crawlOptions.getMaxU() || urlLength > maxUrlLength) {
      return 102;
    }

    long anchorLength = ol.getAnchor().length();
    if (anchorLength < crawlOptions.getMinA() || anchorLength > crawlOptions.getMaxA()) {
      return 103;
    }

    if (oldOutlinks.contains(ol.getToUrl())) {
      return 104;
    }

    String destHost = URLUtil.getHost(toUrl, groupMode);
    if (destHost.isEmpty()) {
      return 107;
    }

    if(ignoreExternalLinks && !sourceHost.equals(destHost)) {
      return 108;
    }

    return 0;
  }
}
