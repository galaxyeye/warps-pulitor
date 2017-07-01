package org.warps.monitor.common.options;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.pulsar.common.Parameterized;
import org.warps.pulsar.common.StringUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by vincent on 17-4-12.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class PulsarOptions implements Parameterized {
  public static final Logger LOG = LoggerFactory.getLogger(PulsarOptions.class);

  public static final String DEFAULT_DELIMETER = " ";

  protected boolean expandAtSign = true;
  protected String[] args;
  protected Set<Object> objects = new HashSet<>();
  protected JCommander jc;

  public PulsarOptions() {
    this.args = new String[]{};
  }

  public PulsarOptions(String args) {
    this.args = args.split("\\s+");
  }

  public PulsarOptions(String[] args) {
    this.args = args;
  }

  public PulsarOptions(Map<String, String> args) {
    this(args.entrySet().stream()
        .map(e -> e.getKey() + DEFAULT_DELIMETER + e.getValue())
        .collect(Collectors.joining(DEFAULT_DELIMETER)));
  }

  public void setExpandAtSign(boolean expandAtSign) {
    this.expandAtSign = expandAtSign;
  }

  public void setObjects(Object... objects) {
    this.objects.clear();
    this.objects.addAll(Lists.newArrayList(objects));
  }

  public void addObjects(Object... objects) {
    this.objects.addAll(Lists.newArrayList(objects));
  }

  public void parseOrExit() {
    parseOrExit(Sets.newHashSet());
  }

  public boolean parse() {
    return parse(false);
  }

  public boolean parseSilent() {
    return parse(true);
  }

  public boolean parse(boolean silent) {
    try {
      if (objects.isEmpty()) {
        objects.add(this);
      }

      if (jc == null) {
        jc = new JCommander(objects);
      }

      jc.setExpandAtSign(expandAtSign);
      jc.parse(args);

      return true;
    }
    catch (Throwable e) {
      if (silent) {
        LOG.warn(StringUtil.stringifyException(e));
      }
      else {
        throw e;
      }
    }

    return false;
  }

  protected void parseOrExit(Set<Object> objects) {
    try {
      if (objects.isEmpty()) {
        objects.add(this);
      }

      if (jc == null) {
        jc = new JCommander(objects);
      }

      jc.setExpandAtSign(expandAtSign);
      jc.parse(args);

      if (isHelp()) {
        jc.usage();
        System.exit(0);
      }
    }
    catch (ParameterException e) {
      System.out.println(e.toString());
      System.exit(0);
    }
  }

  public boolean isHelp() { return false; }

  public void usage() { jc.usage(); }

  public String getArgsLine() {
    return getParams().withKVDelimiter(" ").formatAsLine().replaceAll("\\s+", " ");
  }

  public String[] getArgs() {
    return getParams().withKVDelimiter(" ").formatAsLine().replaceAll("\\s+", " ").split("\\s+");
  }

  @Override
  public boolean equals(Object other) {
    return (other instanceof PulsarOptions) && this.toString().equals(other.toString());
  }

  @Override
  public String toString() { return StringUtils.join(args, " "); }
}
