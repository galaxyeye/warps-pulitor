package org.warps.monitor.common;

import org.warps.monitor.common.options.CrawlOptions;
import org.warps.pulsar.common.ObjectCache;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.pulsar.persist.Outlink;
import org.warps.pulsar.persist.WebPage;
import org.warps.pulsar.persist.gora.db.WebDb;
import org.warps.pulsar.persist.gora.generated.GoraOutlink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by vincent on 17-6-18.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class PageIndexer {

  private final static int MAX_OUTLINKS = 20000;
  private WebDb webDb;
  private String homeUrl;

  private PageIndexer(String homeUrl, WebDb webDb) {
    this.webDb = webDb;
    this.homeUrl = homeUrl;
  }

  public static PageIndexer getInstance(String homeUrl, WebDb webDb, PulsarConfiguration conf) {
    ObjectCache objectCache = ObjectCache.get(conf);
    String cacheId = PageIndexer.class.getName() + "#" + homeUrl;
    PageIndexer pageIndexer = (PageIndexer)objectCache.getObject(cacheId);
    if (pageIndexer == null) {
      pageIndexer = new PageIndexer(homeUrl, webDb);
      objectCache.setObject(cacheId, pageIndexer);
    }
    return pageIndexer;
  }

  synchronized public void index(int pageNo, String... configuredUrls) {
    ArrayList<Outlink> outlinks = Stream.of(configuredUrls).map(CrawlOptions::getUrl)
        .distinct()
        .map(Outlink::new)
        .collect(Collectors.toCollection(ArrayList::new));
    updateAll(pageNo, outlinks, false);
  }

  private void updateAll(int pageNo, Collection<Outlink> newOutlinks, boolean remove) {
    WebPage indexPage = getIndex(pageNo);

    List<Outlink> outlinks = indexPage.getOutlinks().stream().map(Outlink::box).collect(Collectors.toList());
    if (remove) {
      outlinks.removeAll(newOutlinks);
    }
    else {
      outlinks.addAll(0, newOutlinks);
    }
    List<GoraOutlink> newGoraOutlinks = outlinks.stream().distinct().map(Outlink::unbox).collect(Collectors.toList());
    indexPage.setOutlinks(newGoraOutlinks);

    String message = "Total " + indexPage.getOutlinks().size() + " outlinks\n";
    indexPage.setTextCascaded(message);

    webDb.put(indexPage.url(), indexPage, remove);
    webDb.flush();
  }

  private WebPage getHome() {
    WebPage home = webDb.get(homeUrl);
    if (home == null) {
      home = WebPage.createInternalPage(homeUrl, "Pulsar Index Home", "");
    }

    // Keep at most 20000 outlinks
    if (home.getOutlinks().size() > MAX_OUTLINKS) {
      home.setOutlinks(home.getOutlinks().subList(0, (int) (MAX_OUTLINKS * 0.80)));
    }

    webDb.put(homeUrl, home);

    return home;
  }

  private WebPage getIndex(int pageNo) {
    return getIndex(pageNo, "Index Page " + pageNo, "");
  }

  private WebPage getIndex(int pageNo, String pageTitle, String content) {
    String url = homeUrl + "/" + pageNo;

    WebPage indexPage = webDb.get(url);
    if (indexPage == null) {
      indexPage = WebPage.createInternalPage(homeUrl + "/" + pageNo, pageTitle, content);

      WebPage home = getHome();
      home.getOutlinks().add(0, Outlink.parse(url).unbox());
      webDb.put(homeUrl, home);
      webDb.put(url, indexPage);
      webDb.flush();
    }

    return indexPage;
  }
}
