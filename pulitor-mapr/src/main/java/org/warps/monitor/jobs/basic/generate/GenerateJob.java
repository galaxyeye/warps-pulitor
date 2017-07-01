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
package org.warps.monitor.jobs.basic.generate;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.monitor.common.PulsarFiles;
import org.warps.monitor.jobs.common.JobUtils;
import org.warps.monitor.jobs.common.SelectorEntry;
import org.warps.monitor.jobs.common.URLPartitioner;
import org.warps.monitor.jobs.core.PulsarJob;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.pulsar.persist.gora.generated.GoraWebPage;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.warps.pulsar.common.PulsarConfiguration.*;
import static org.warps.pulsar.common.PulsarConstants.DISTANCE_INFINITE;
import static org.warps.pulsar.common.PulsarParams.*;

public final class GenerateJob extends PulsarJob {

  public static final Logger LOG = LoggerFactory.getLogger(GenerateJob.class);

  @Parameter(names = ARG_CRAWL_ID, description = "The crawl id, (default : \"storage.crawl.id\").")
  private String crawlId = null;
  @Parameter(names = ARG_BATCH_ID, description = "The batch id")
  private String batchId = JobUtils.generateBatchId();
  @Parameter(names = ARG_TOPN, description = "Number of top URLs to be selected")
  private int topN = Integer.MAX_VALUE;
  @Parameter(names = ARG_NO_NORMALIZER, description = "Activate the normalizer plugin to normalize the url")
  private boolean noNormalizer = false;
  @Parameter(names = ARG_NO_FILTER, description = "Activate the filter plugin to filter the url")
  private boolean noFilter = false;
  @Parameter(names = {"-help", "-h"}, help = true, description = "print the help information")
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
    conf.set(BATCH_ID, batchId);
    conf.setInt(GENERATE_TOP_N, topN);
    conf.setBoolean(GENERATE_FILTER, !noFilter);
    conf.setBoolean(GENERATE_NORMALISE, !noNormalizer);

    PulsarFiles.writeBatchId(batchId);

    LOG.info(Params.format(
        "className", this.getClass().getSimpleName(),
        "crawlId", conf.get(CRAWL_ID),
        "batchId", conf.get(BATCH_ID),
        "filter", !noFilter,
        "normaliser", !noNormalizer,
        "topN", topN,
        "maxDistance", conf.getUint(CRAWL_MAX_DISTANCE, DISTANCE_INFINITE)
    ));
  }

  @Override
  public void initJob() throws Exception {
    initMapperJob(currentJob, FIELDS, SelectorEntry.class,
        GoraWebPage.class, GenerateMapper.class, URLPartitioner.SelectorEntryPartitioner.class,
        getInactiveFilter(), false);
    initReducerJob(currentJob, GenerateReducer.class);
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

    PulsarJob.run(conf, new GenerateJob(), args);
  }
}
