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
package org.warps.monitor.jobs.basic.inject;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.warps.monitor.common.CommonCounter;
import org.warps.monitor.common.PageIndexer;
import org.warps.monitor.crawl.inject.SeedBuilder;
import org.warps.monitor.jobs.core.PulsarMapper;
import org.warps.pulsar.persist.WebPage;
import org.warps.pulsar.persist.gora.db.WebDb;
import org.warps.pulsar.persist.gora.generated.GoraWebPage;

import java.io.IOException;

import static org.warps.pulsar.common.PulsarConstants.SEED_HOME_URL;

/**
 * Created by vincent on 17-4-13.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class InjectMapper extends PulsarMapper<LongWritable, Text, String, GoraWebPage> {
  private SeedBuilder seedBuiler;
  private WebDb webDb;
  private PageIndexer pageIndexer;

  @Override
  public void setup(Context context) {
    this.seedBuiler = new SeedBuilder(conf);
    this.webDb = WebDb.create(conf);
    this.pageIndexer = PageIndexer.getInstance(SEED_HOME_URL, webDb, conf);
  }

  protected void map(LongWritable key, Text line, Context context) throws IOException, InterruptedException {
    pulsarCounters.increase(CommonCounter.mRows);

    WebPage seedPage = seedBuiler.create(line.toString());
    // LOG.debug("Injecting: " + seedPage.getStatusRepresentation());
    context.write(seedPage.reversedUrl(), seedPage.get());
    pageIndexer.index(1, seedPage.url());
    pulsarCounters.increase(CommonCounter.mPersist);
  }
}
