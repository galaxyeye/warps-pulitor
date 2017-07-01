package org.warps.monitor.common.options;

import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.warps.monitor.common.options.converters.DurationConverter;
import org.warps.monitor.common.options.converters.WeightedKeywordsConverter;
import org.warps.pulsar.common.ObjectCache;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.PulsarConfiguration;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ArrayUtils.INDEX_NOT_FOUND;
import static org.warps.pulsar.common.PulsarConstants.FETCH_PRIORITY_DEFAULT;

/**
 * Created by vincent on 17-3-18.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class CrawlOptions extends CommonPulsarOptions {

  public static CrawlOptions DEFAULT = new CrawlOptions();

  @Parameter(required = true, description = "Seed urls")
  private List<String> urls = new ArrayList<>();
  @Parameter(names = {"-log", "-verbose"}, description = "Log level for this crawl task")
  private int verbose = 0;

  @Parameter(names = {"-i", "--fetch-interval"}, converter = DurationConverter.class, description = "Fetch interval")
  private Duration fetchInterval = Duration.ofHours(1);
  @Parameter(names = {"-p", "--fetch-priority"}, description = "Fetch priority")
  private int fetchPriority = FETCH_PRIORITY_DEFAULT;
  @Parameter(names = {"-s", "--score"}, description = "Injected score")
  private int score = 0;
  @Parameter(names = {"-d", "--depth"}, description = "Max crawl depth. Do not crawl anything deeper")
  private int depth = 1;
  @Parameter(names = {"-z", "--zone-id"}, description = "The zone id of the website we crawl")
  private String zoneId = ZoneId.systemDefault().getId();

  @Parameter(names = {"-fd", "--follow-dom"}, description = "Path to the main area of links to follow")
  private String urlDom = "body";

  @Parameter(names = {"-w", "--keywords"}, converter = WeightedKeywordsConverter.class, description = "Keywords with weight, ")
  private Map<String, Double> keywords = new HashMap<>();

  @Parameter(names = {"-idx", "--indexer-url"}, description = "Indexer url")
  private String indexerUrl;

  private LinkFilterOptions linkFilterOptions = new LinkFilterOptions();

  private EntityOptions entityOptions = new EntityOptions();

  public CrawlOptions() {
    super();
  }

  public CrawlOptions(String args) {
    super(args);
  }

  public CrawlOptions(String args, PulsarConfiguration conf) {
    super(args);
    this.init();
  }

  public CrawlOptions(String[] args, PulsarConfiguration conf) {
    super(args);
    this.init();
  }

  private void init() {
    this.linkFilterOptions = new LinkFilterOptions("");

    addObjects(this, linkFilterOptions, entityOptions, entityOptions.getCollectionOptions());
  }

  public static CrawlOptions parse(String configuredUrls, PulsarConfiguration conf) {
    if (StringUtils.isBlank(configuredUrls)) {
      return new CrawlOptions(new String[0], conf);
    }

    ObjectCache objectCache = ObjectCache.get(conf);
    final String cacheId = configuredUrls;

    CrawlOptions crawlOptions = objectCache.getObject(cacheId, null);

    if (crawlOptions == null) {
      crawlOptions = new CrawlOptions(configuredUrls, conf);
      crawlOptions.parseSilent();
      objectCache.setObject(cacheId, crawlOptions);
    }

    return crawlOptions;
  }

  public static String getUrl(String configuredUrl) {
    return StringUtils.substringBefore(configuredUrl, " ");
  }

  public static String getArgs(String configuredUrl) {
    return StringUtils.substringAfter(configuredUrl, " ");
  }

  public static Pair<String, String> split(String configuredUrl) {
    int pos = StringUtils.indexOf(configuredUrl, " ");
    if (pos != INDEX_NOT_FOUND) {
      return Pair.of(configuredUrl.substring(0, pos), configuredUrl.substring(pos));
    }
    return Pair.of(configuredUrl, "");
  }

  public String getUrl() { return urls.isEmpty() ? "" : urls.get(0); }

  public int getVerbose() { return verbose; }

  public Duration getFetchInterval() { return fetchInterval; }

  public int getFetchPriority() { return fetchPriority; }

  public int getScore() { return score; }

  public int getDepth() { return depth; }

  public String getZoneId() { return zoneId; }

  public String getUrlDom() { return urlDom; }

  @Deprecated
  public int getMinU() { return linkFilterOptions.getMinUrlLength(); }

  @Deprecated
  public int getMaxU() { return linkFilterOptions.getMaxUrlLength(); }

  @Deprecated
  public int getMinA() { return linkFilterOptions.getMinAnchorLength(); }

  @Deprecated
  public int getMaxA() { return linkFilterOptions.getMaxAnchorLength(); }

  public Map<String, Double> getKeywords() { return keywords; }

  public String getIndexerUrl() { return indexerUrl; }

  public LinkFilterOptions getLinkFilterOptions() { return linkFilterOptions; }

  public EntityOptions getEntityOptions() { return entityOptions; }

  private void afterInject() {
    entityOptions.afterInject();
  }

  public String formatKeywords() {
    final DecimalFormat df = new DecimalFormat("##.#");
    return keywords.entrySet()
        .stream().map(e -> e.getKey() + "^" + df.format(e.getValue())).collect(Collectors.joining(","));
  }

  @Override
  public Params getParams() {
    return Params.of(
        "-log", verbose,
        "-i", fetchInterval,
        "-p", fetchPriority,
        "-s", score,
        "-d", depth,
        "-z", zoneId,
        "-fd", urlDom,
        "-w", formatKeywords(),
        "-idx", indexerUrl
    )
        .filter(p -> p.getValue() != null)
        .filter(p -> !p.getValue().toString().isEmpty())
        .merge(linkFilterOptions.getParams())
        .merge(entityOptions.getParams())
        ;
  }

  @Override
  public String toString() {
    String opts = getParams().withKVDelimiter(" ").formatAsLine().replaceAll("\\s+", " ");
    if (!getUrl().isEmpty()) {
      opts = getUrl() + " " + opts;
    }
    return opts;
  }
}
