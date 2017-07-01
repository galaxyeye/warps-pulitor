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

import org.warps.pulsar.common.PulsarConfiguration;

import java.io.IOException;

/**
 * Created by vincent on 16-9-24.
 * Copyright @ 2013-2016 Warpspeed Information. All rights reserved
 */
public interface PulsarContext<KEYIN, VALUEIN, KEYOUT, VALUEOUT> {
  PulsarConfiguration getConfiguration();
  boolean nextKey() throws IOException, InterruptedException;
  boolean nextKeyValue() throws IOException, InterruptedException;
  KEYIN getCurrentKey() throws IOException, InterruptedException;
  VALUEIN getCurrentValue() throws IOException, InterruptedException;
  void write(KEYOUT var1, VALUEOUT var2) throws IOException, InterruptedException;
  Iterable<VALUEIN> getValues() throws IOException, InterruptedException;
  void setStatus(String var1);
  String getStatus();
  int getJobId();
  String getJobName();
}
