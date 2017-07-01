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
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.StringUtil;
import org.warps.monitor.common.PulsarCounters;
import org.warps.monitor.jobs.core.GoraReducer;
import org.warps.monitor.jobs.basic.fetch.FetchMapper;
import org.warps.monitor.jobs.common.SelectorEntry;
import org.warps.pulsar.persist.WebPage;
import org.warps.pulsar.persist.gora.generated.GoraWebPage;
import org.warps.pulsar.persist.metadata.Mark;

import java.io.IOException;

import static org.warps.pulsar.common.PulsarConfiguration.BATCH_ID;
import static org.warps.pulsar.common.PulsarConfiguration.GENERATE_TOP_N;
import static org.warps.pulsar.common.PulsarConstants.ALL_BATCHES;
import static org.warps.pulsar.common.UrlUtil.reverseUrl;

/**
 * Reduce class for generate
 *
 * The #reduce() method write a random integer to all generated URLs. This
 * random number is then used by {@link FetchMapper}.
 */
public class GenerateReducer extends GoraReducer<SelectorEntry, GoraWebPage, String, GoraWebPage> {

  public static final Logger LOG = GenerateJob.LOG;

  private enum Counter { rHosts, rUrlMalformed, rSeeds, rFromSeed }
  static { PulsarCounters.register(Counter.class); }

  private int limit;
  private String batchId;
  private int count = 0;

  @Override
  protected void setup(Context context) throws IOException, InterruptedException {
    batchId = conf.get(BATCH_ID, ALL_BATCHES);

    // Generate top N links only
    limit = conf.getUint(GENERATE_TOP_N, Integer.MAX_VALUE);
    limit /= context.getNumReduceTasks();

    LOG.info(Params.format(
        "className", this.getClass().getSimpleName(),
        "batchId", batchId,
        "limit", limit
    ));
  }

  @Override
  protected void reduce(SelectorEntry key, Iterable<GoraWebPage> rows, Context context) throws IOException, InterruptedException {

    String url = key.getUrl();

    for (GoraWebPage row : rows) {
      WebPage page = WebPage.wrap(url, row, false);

      try {
        pulsarCounters.increase(CommonCounter.rRows);

        if (count >= limit) {
          stop("Enough pages generated, quit");
          break;
        }

        page.setBatchId(batchId);
        page.setGenerateTime(startTime);

        page.removeMark(Mark.INJECT);
        page.putMark(Mark.GENERATE, batchId);

        context.write(reverseUrl(url), page.get());

        pulsarCounters.increase(CommonCounter.rPersist);

        ++count;
      }
      catch (Throwable e) {
        LOG.error(StringUtil.stringifyException(e));
      }
    } // for
  }
}
