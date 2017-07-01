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

import org.slf4j.Logger;
import org.warps.monitor.common.CommonCounter;
import org.warps.monitor.common.PulsarCounters;
import org.warps.monitor.jobs.common.SelectorEntry;
import org.warps.monitor.jobs.core.GoraMapper;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.persist.WebPage;
import org.warps.pulsar.persist.gora.generated.GoraWebPage;

import java.io.IOException;

public class GenerateMapper extends GoraMapper<String, GoraWebPage, SelectorEntry, GoraWebPage> {
  public static final Logger LOG = GenerateJob.LOG;

  private enum Counter { mLater, redundant }
  static { PulsarCounters.register(Counter.class); }

  @Override
  public void setup(Context context) throws IOException, InterruptedException {
    Params.of(
        "className", this.getClass().getSimpleName()
    ).withLogger(LOG).info();
  }

  @Override
  public void map(String reversedUrl, GoraWebPage row, Context context) throws IOException, InterruptedException {
    pulsarCounters.increase(CommonCounter.mRows);

    WebPage page = WebPage.wrap(reversedUrl, row, true);
    String url = page.url();

    if (!page.isSeed() && page.getFetchCount() > 0) {
      pulsarCounters.increase(Counter.redundant);
      return;
    }

    if (page.getFetchTime().isAfter(startTime)) {
      pulsarCounters.increase(Counter.mLater);
      return;
    }

    LOG.debug("Generate: " + url);

    context.write(new SelectorEntry(url, 0.0f), page.get());

    pulsarCounters.increase(CommonCounter.mPersist);
  }
}
