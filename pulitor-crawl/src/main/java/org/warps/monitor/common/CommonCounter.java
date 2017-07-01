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

/**
 * Created by vincent on 17-4-5.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public enum CommonCounter {
  mRows,
  rRows,

  mDetail,
  rDetail,

  mPersist,
  rPersist,

  mDepth0, mDepth1, mDepth2, mDepth3, mDepthN,
  rDepth0, rDepth1, rDepth2, rDepth3, rDepthN,

  mDay0, mDay1, mDay2, mDay3, mDay4, mDay5, mDay6, mDay7, mDayN,
  rDay0, rDay1, rDay2, rDay3, rDay4, rDay5, rDay6, rDay7, rDayN,

  mInlinks,
  rInlinks,

  mOutlinks,
  rOutlinks,

  errors,

  stFetched,
  stRedirTemp,
  stRedirPerm,
  stNotModified,
  stRetry,
  stUnfetched,
  stGone;

  static { PulsarCounters.register(CommonCounter.class); }
}
