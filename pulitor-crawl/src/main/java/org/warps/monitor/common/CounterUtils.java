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
package org.warps.monitor.common;

import org.warps.pulsar.persist.CrawlStatus;

/**
 * Created by vincent on 17-4-5.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class CounterUtils {

  public static void increaseMDays(long days, PulsarCounters pulsarCounters) {
    CommonCounter counter;
    if (days == 0) {
      counter = CommonCounter.mDay0;
    }
    else if (days == 1) {
      counter = CommonCounter.mDay1;
    }
    else if (days == 2) {
      counter = CommonCounter.mDay2;
    }
    else if (days == 3) {
      counter = CommonCounter.mDay3;
    }
    else if (days == 4) {
      counter = CommonCounter.mDay4;
    }
    else if (days == 5) {
      counter = CommonCounter.mDay5;
    }
    else if (days == 6) {
      counter = CommonCounter.mDay6;
    }
    else if (days == 7) {
      counter = CommonCounter.mDay7;
    }
    else {
      counter = CommonCounter.mDayN;
    }

    pulsarCounters.increase(counter);
  }

  public static void increaseRDays(long days, PulsarCounters pulsarCounters) {
    CommonCounter counter;
    if (days == 0) {
      counter = CommonCounter.rDay0;
    }
    else if (days == 1) {
      counter = CommonCounter.rDay1;
    }
    else if (days == 2) {
      counter = CommonCounter.rDay2;
    }
    else if (days == 3) {
      counter = CommonCounter.rDay3;
    }
    else if (days == 4) {
      counter = CommonCounter.rDay4;
    }
    else if (days == 5) {
      counter = CommonCounter.rDay5;
    }
    else if (days == 6) {
      counter = CommonCounter.rDay6;
    }
    else if (days == 7) {
      counter = CommonCounter.rDay7;
    }
    else {
      counter = CommonCounter.rDayN;
    }

    pulsarCounters.increase(counter);
  }

  public static void increaseMDepth(int depth, PulsarCounters pulsarCounters) {
    CommonCounter counter;
    if (depth == 0) {
      counter = CommonCounter.mDepth0;
    }
    else if (depth == 1) {
      counter = CommonCounter.mDepth1;
    }
    else if (depth == 2) {
      counter = CommonCounter.mDepth2;
    }
    else if (depth == 3) {
      counter = CommonCounter.mDepth3;
    }
    else {
      counter = CommonCounter.mDepthN;
    }

    pulsarCounters.increase(counter);
  }

  public static void increaseRDepth(int depth, PulsarCounters counter) {
    if (depth == 0) {
      counter.increase(CommonCounter.rDepth0);
    }
    else if (depth == 1) {
      counter.increase(CommonCounter.rDepth1);
    }
    else if (depth == 2) {
      counter.increase(CommonCounter.rDepth2);
    }
    else if (depth == 3) {
      counter.increase(CommonCounter.rDepth3);
    }
    else {
      counter.increase(CommonCounter.rDepthN);
    }
  }

  public static void updateStatusCounter(byte status, PulsarCounters counter) {
    switch (status) {
      case CrawlStatus.STATUS_FETCHED:
        counter.increase(CommonCounter.stFetched);
        break;
      case CrawlStatus.STATUS_REDIR_TEMP:
        counter.increase(CommonCounter.stRedirTemp);
        break;
      case CrawlStatus.STATUS_REDIR_PERM:
        counter.increase(CommonCounter.stRedirPerm);
        break;
      case CrawlStatus.STATUS_NOTMODIFIED:
        counter.increase(CommonCounter.stNotModified);
        break;
      case CrawlStatus.STATUS_RETRY:
        counter.increase(CommonCounter.stRetry);
        break;
      case CrawlStatus.STATUS_UNFETCHED:
        counter.increase(CommonCounter.stUnfetched);
        break;
      case CrawlStatus.STATUS_GONE:
        counter.increase(CommonCounter.stGone);
        break;
      default:
        break;
    }
  }
}
