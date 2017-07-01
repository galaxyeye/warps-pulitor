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
package org.warps.monitor.jobs.basic.inject;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.apache.gora.mapreduce.GoraOutputFormat;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.monitor.jobs.core.PulsarJob;
import org.warps.pulsar.common.DateTimeUtil;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.pulsar.persist.gora.generated.GoraWebPage;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.warps.pulsar.common.PulsarConfiguration.CRAWL_ID;
import static org.warps.pulsar.common.PulsarParams.ARG_CRAWL_ID;

public final class InjectJob extends PulsarJob {

  public static final Logger LOG = LoggerFactory.getLogger(InjectJob.class);

  @Parameter(required = true, description = "<seeds> \nSeed urls. Use {@code @FILE} syntax to read from file.")
  private List<String> seeds = new ArrayList<>();
  @Parameter(names = {ARG_CRAWL_ID}, description = "crawl id, (default : \"storage.crawl.id\").")
  private String crawlId = null;
  @Parameter(names = {"-help", "-h"}, help = true, description = "print the help information")
  private boolean help;

  private File seedFile;

  @Override
  public void setup(Params params) throws Exception {
    conf.setIfNotNull(CRAWL_ID, crawlId);

    seedFile = File.createTempFile("seed", ".txt");
    Files.write(seedFile.toPath(), StringUtils.join(seeds, "\n").getBytes());

    LOG.info(Params.format(
        "className", this.getClass().getSimpleName(),
        "crawlId", crawlId,
        "seedFile", seedFile,
        "jobStartTime", DateTimeUtil.format(startTime)
    ));
  }

  @Override
  public void initJob() throws Exception {
    FileInputFormat.addInputPath(currentJob, new Path(seedFile.getAbsolutePath()));
    currentJob.setMapperClass(InjectMapper.class);
    currentJob.setMapOutputKeyClass(String.class);
    currentJob.setMapOutputValueClass(GoraWebPage.class);
    currentJob.setOutputFormatClass(GoraOutputFormat.class);

    currentJob.setReducerClass(Reducer.class);
    GoraOutputFormat.setOutput(currentJob, store, true);
    currentJob.setNumReduceTasks(0);
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

    PulsarJob.run(conf, new InjectJob(), args);
  }
}
