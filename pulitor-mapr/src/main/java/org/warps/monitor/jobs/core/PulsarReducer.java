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
package org.warps.monitor.jobs.core;

import org.apache.gora.store.DataStore;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.monitor.common.PulsarCounters;
import org.warps.monitor.common.PulsarReporter;
import org.warps.pulsar.common.DateTimeUtil;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.pulsar.persist.gora.GoraStorage;
import org.warps.pulsar.persist.gora.generated.GoraWebPage;

import java.io.IOException;
import java.time.Instant;

import static org.warps.pulsar.common.PulsarConfiguration.BATCH_ID;
import static org.warps.pulsar.common.PulsarConfiguration.CRAWL_ID;

public class PulsarReducer<K1, V1, K2, V2> extends Reducer<K1, V1, K2, V2> {

  protected static final Logger LOG = LoggerFactory.getLogger(PulsarReducer.class.getName());

  protected Context context;

  protected PulsarConfiguration conf;
  protected PulsarCounters pulsarCounters;
  protected PulsarReporter pulsarReporter;
  protected DataStore<String, GoraWebPage> store;

  protected boolean completed = false;

  protected Instant startTime = Instant.now();

  protected void beforeSetup(Context context) throws IOException, InterruptedException, ClassNotFoundException {
    this.context = context;
    this.conf = new PulsarConfiguration(context.getConfiguration());
    store = GoraStorage.createDataStore(conf, String.class, GoraWebPage.class);

    this.pulsarCounters = new PulsarCounters();
    this.pulsarReporter = new PulsarReporter(context.getJobName(), pulsarCounters, conf, context);

    LOG.info(Params.formatAsLine(
        "---- reducer setup ", " ----",
        "className", this.getClass().getSimpleName(),
        "startTime", DateTimeUtil.format(startTime),
        "reducerTasks", context.getNumReduceTasks(),
        "hostname", pulsarCounters.getHostname(),
        "config", conf,
        "configId", conf.get("pulsar.config.id"),
        "crawlId", conf.get(CRAWL_ID),
        "batchId", conf.get(BATCH_ID),
        "realSchema", store.getSchemaName(),
        "storeClass", store.getClass().getName()
    ));
  }

  @Override
  public void run(Context context) {
    try {
      beforeSetup(context);
      setup(context);

      doRun(context);

      cleanup(context);

      cleanupContext(context);
    } catch (Throwable e) {
      LOG.error(StringUtils.stringifyException(e));
    }
    finally {
      afterCleanup(context);
    }
  }

  protected void doRun(Context context) throws IOException, InterruptedException {
    while (!completed && context.nextKey()) {
      reduce(context.getCurrentKey(), context.getValues(), context);
    }
  }

  protected void cleanupContext(Context context) throws Exception {
  }

  protected void afterCleanup(Context context) {
    context.setStatus(pulsarCounters.getStatus(true));
    pulsarReporter.stopReporter();

    LOG.info(Params.formatAsLine(
        "---- reducer cleanup ", " ----",
        "className", this.getClass().getSimpleName(),
        "startTime", DateTimeUtil.format(startTime),
        "finishTime", DateTimeUtil.now(),
        "timeElapsed", DateTimeUtil.elapsedTime(startTime)
    ));
  }

  protected boolean completed() {
    return completed;
  }

  protected void abort() {
    completed = true;
  }

  protected void abort(String error) {
    LOG.error(error);
    completed = true;
  }

  protected void stop() {
    completed = true;
  }

  protected void stop(String info) {
    LOG.info(info);
    completed = true;
  }
}
