package org.warps.monitor.common.options;

import com.beust.jcommander.Parameter;
import org.warps.pulsar.persist.Outlink;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.PulsarConfiguration;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import static org.warps.pulsar.common.PulsarConfiguration.PARSE_MAX_ANCHOR_LENGTH;
import static org.warps.pulsar.common.PulsarConfiguration.PARSE_MIN_ANCHOR_LENGTH;
import static org.warps.pulsar.common.PulsarConstants.SHORTEST_VALID_URL_LENGTH;

/**
 * Created by vincent on 17-3-18.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class LinkFilterOptions extends PulsarOptions {

  public static LinkFilterOptions DEFAULT = new LinkFilterOptions();

  @Parameter(names = {"-amin", "--anchor-min-length"}, description = "Anchor min length")
  private int minAnchorLength = 5;
  @Parameter(names = {"-amax", "--anchor-max-length"}, description = "Anchor max length")
  private int maxAnchorLength = 50;
  @Parameter(names = {"-umin", "--url-min-length"}, description = "Url min length")
  private int minUrlLength = SHORTEST_VALID_URL_LENGTH;
  @Parameter(names = {"-umax", "--url-max-length"}, description = "Url max length")
  private int maxUrlLength = 200;
  @Parameter(names = {"-upre", "--url-prefix"}, description = "Url prefix")
  private String urlPrefix = "";
  @Parameter(names = {"-ucon", "--url-contains"}, description = "Url contains")
  private String urlContains = "";
  @Parameter(names = {"-upos", "--url-postfix"}, description = "Url postfix")
  private String urlPostfix = "";
  @Parameter(names = {"-ureg", "--url-regex"}, description = "Url regex")
  private String urlRegex = "";

  private List<String> filterReport = new LinkedList<>();

  public LinkFilterOptions() {
  }

  public LinkFilterOptions(String args) {
    super(args);
  }

  private void init(PulsarConfiguration conf) {
    this.minAnchorLength = conf.getUint(PARSE_MIN_ANCHOR_LENGTH, 8);
    this.maxAnchorLength = conf.getUint(PARSE_MAX_ANCHOR_LENGTH, 40);
  }

  public int getMinAnchorLength() {
    return minAnchorLength;
  }

  public int getMaxAnchorLength() {
    return maxAnchorLength;
  }

  public int getMinUrlLength() {
    return minUrlLength;
  }

  public int getMaxUrlLength() {
    return maxUrlLength;
  }

  public String getUrlPrefix() {
    return urlPrefix;
  }

  public String getUrlContains() {
    return urlContains;
  }

  public String getUrlPostfix() {
    return urlPostfix;
  }

  public String getUrlRegex() {
    return urlRegex;
  }

  public int filter(Outlink ol) {
    return filter(ol.getToUrl(), ol.getAnchor());
  }

  public int filter(String url, String anchor) {
    if (anchor.length() < minAnchorLength || anchor.length() > maxAnchorLength) {
      return 100;
    }

    return filter(url);
  }

  public int filter(String url) {
    if (url.length() < minUrlLength || url.length() > maxUrlLength) {
      return 200;
    }

    if (!urlPrefix.isEmpty() && !url.startsWith(urlPrefix)) {
      return 210;
    }

    if (!urlPostfix.isEmpty() && !url.endsWith(urlPostfix)) {
      return 211;
    }

    if (!urlContains.isEmpty() && !url.contains(urlContains)) {
      return 212;
    }

    if (!urlRegex.isEmpty() && !url.matches(urlRegex)) {
      return 213;
    }

    return 0;
  }

  public Predicate<String> asUrlPredicate() {
    return url -> {
      int r = this.filter(url);
      filterReport.add(r + " <- " + url);
      return 0 == r;
    };
  }

  public Predicate<Outlink> asLinkPredicate() {
    return ol -> {
      int r = this.filter(ol.getToUrl(), ol.getAnchor());
      filterReport.add(r + " <- " + ol.getToUrl() + "\t" + ol.getAnchor());
      return 0 == r;
    };
  }

  public Params getParams() {
    return Params.of(
        "-amin", minAnchorLength,
        "-amax", maxAnchorLength,
        "-umin", minUrlLength,
        "-umax", maxUrlLength,
        "-upre", urlPrefix,
        "-ucon", urlContains,
        "-upos", urlPostfix,
        "-ureg", urlRegex
    )
        .filter(p -> p.getValue() != null)
        .filter(p -> !p.getValue().toString().isEmpty());
  }

  public String build() {
    return getParams().withKVDelimiter(" ").formatAsLine();
  }

  public List<String> getFilterReport() {
    return filterReport;
  }

  @Override
  public String toString() {
    return build();
  }
}
