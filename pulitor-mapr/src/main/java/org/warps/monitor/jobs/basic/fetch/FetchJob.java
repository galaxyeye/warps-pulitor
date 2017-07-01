/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.warps.monitor.jobs.basic.fetch;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.hadoop.io.IntWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.monitor.common.PulsarFiles;
import org.warps.monitor.crawl.fetch.FetchEntry;
import org.warps.monitor.jobs.common.URLPartitioner;
import org.warps.monitor.jobs.core.PulsarJob;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.pulsar.persist.gora.generated.GoraWebPage;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.warps.pulsar.common.PulsarConfiguration.BATCH_ID;
import static org.warps.pulsar.common.PulsarConfiguration.CRAWL_ID;
import static org.warps.pulsar.common.PulsarParams.ARG_BATCH_ID;
import static org.warps.pulsar.common.PulsarParams.ARG_CRAWL_ID;

/**
 * Fetch job
 * */
public final class FetchJob extends PulsarJob {

  public static final Logger LOG = LoggerFactory.getLogger(FetchJob.class);

  @Parameter(names = ARG_CRAWL_ID, description = "The id to prefix the schemas to operate on")
  private String crawlId = null;
  @Parameter(names = ARG_BATCH_ID, description = "[batchId] \n If batchId is not specified, last generated batch(es) will be fetched.")
  private String batchId = null;
  @Parameter(names = {"-help", "-h"}, help = true, description = "Print this help text")
  private boolean help;

  private static final Collection<GoraWebPage.Field> FIELDS = new HashSet<>();

  static {
    Collections.addAll(FIELDS, GoraWebPage.Field.values());
    FIELDS.remove(GoraWebPage.Field.CONTENT);
    FIELDS.remove(GoraWebPage.Field.PAGE_TEXT);
    FIELDS.remove(GoraWebPage.Field.CONTENT_TEXT);
  }

  @Override
  public void setup(Params params) throws Exception {
    conf.setIfNotNull(CRAWL_ID, crawlId);
    conf.setIfNotNull(BATCH_ID, batchId);
    conf.set(BATCH_ID, conf.get(BATCH_ID, PulsarFiles.readBatchIdOrDefault("all")));

    LOG.info(Params.format(
        "className", this.getClass().getSimpleName(),
        "crawlId", conf.get(CRAWL_ID),
        "batchId", conf.get(BATCH_ID)
    ));
  }

  @Override
  public void initJob() throws Exception {
    String batchId = conf.get(BATCH_ID);

    initMapperJob(currentJob, FIELDS,
        IntWritable.class, FetchEntry.class, FetchMapper.class,
        URLPartitioner.FetchEntryPartitioner.class, getBatchIdFilter(batchId), false);
    initReducerJob(currentJob, FetchReducer.class);
  }

  @Override
  public int run(String[] args) throws Exception {
    JCommander jc = new JCommander(this, args);

    if (this.help) {
      jc.usage();
      return -1;
    }
    return run();
  }

  public static void main(String[] args) throws Exception {
    PulsarConfiguration conf = new PulsarConfiguration();
    conf.addResource("pulsar-default.xml");
    conf.addResource("pulsar-site.xml");
    conf.addResource("pulsar-task.xml");

    PulsarJob.run(conf, new FetchJob(), args);
  }
}
