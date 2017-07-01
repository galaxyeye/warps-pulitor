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
package org.warps.monitor.jobs.common;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Partitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.pulsar.common.UrlUtil;
import org.warps.monitor.common.URLUtil;
import org.warps.monitor.crawl.fetch.FetchEntry;
import org.warps.pulsar.persist.gora.generated.GoraWebPage;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Partition urls by host, domain name or IP depending on the value of the
 * parameter 'partition.url.mode' which can be 'BY_HOST', 'byDomain' or 'byIP'
 */
public class URLPartitioner implements Configurable {
  private static final Logger LOG = LoggerFactory.getLogger(URLPartitioner.class);

  public static final String PARTITION_MODE_KEY = "partition.url.mode";

  public static final String PARTITION_URL_SEED = "partition.url.seed";

  private Configuration conf;

  private int seed;
  private URLUtil.GroupMode groupMode = URLUtil.GroupMode.BY_HOST;

  public URLPartitioner() {}

  public URLPartitioner(Configuration conf) {
    setConf(conf);
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
    seed = conf.getInt(PARTITION_URL_SEED, 0);
    groupMode = conf.getEnum(PARTITION_MODE_KEY, URLUtil.GroupMode.BY_HOST);
  }

  /**
   * Get a hash number in [0, numReduceTasks) for a url
   * */
  public int getPartition(String urlString, int numReduceTasks) {
    if (numReduceTasks == 1) {
      // this check can be removed when we use Hadoop with MAPREDUCE-1287
      return 0;
    }

    URL url = URLUtil.getURLOrNull(urlString);
    if (url == null) {
      return new Random().nextInt(numReduceTasks);
    }

    int hashCode = urlString.hashCode();
    if (groupMode == URLUtil.GroupMode.BY_HOST) {
      hashCode = url.getHost().hashCode();
    } else if (groupMode == URLUtil.GroupMode.BY_DOMAIN) {
      hashCode = URLUtil.getDomainName(url).hashCode();
    } else { // MODE IP
      try {
        InetAddress address = InetAddress.getByName(url.getHost());
        hashCode = address.getHostAddress().hashCode();
      } catch (UnknownHostException e) {
        LOG.info("Couldn't find IP for host: " + url.getHost());
      }
    }

    // make hosts wind up in different partitions on different runs
    hashCode ^= seed;
    return (hashCode & Integer.MAX_VALUE) % numReduceTasks;
  }

  public static class SelectorEntryPartitioner extends Partitioner<SelectorEntry, GoraWebPage> implements Configurable {
    private URLPartitioner partitioner = new URLPartitioner();
    private Configuration conf;

    @Override
    public int getPartition(SelectorEntry selectorEntry, GoraWebPage page, int numReduces) {
      return partitioner.getPartition(selectorEntry.getUrl(), numReduces);
    }

    @Override
    public Configuration getConf() {
      return conf;
    }

    @Override
    public void setConf(Configuration conf) {
      this.conf = conf;
      partitioner.setConf(conf);
    }
  }

  public static class FetchEntryPartitioner extends Partitioner<IntWritable, FetchEntry> implements Configurable {
    private URLPartitioner partitioner = new URLPartitioner();
    private Configuration conf;

    @Override
    public int getPartition(IntWritable intWritable, FetchEntry fetchEntry, int numReduces) {
      String key = fetchEntry.getReservedUrl();
      String url = UrlUtil.unreverseUrl(key);
      return partitioner.getPartition(url, numReduces);
    }

    @Override
    public Configuration getConf() {
      return conf;
    }

    @Override
    public void setConf(Configuration conf) {
      this.conf = conf;
      partitioner.setConf(conf);
    }
  }
}
