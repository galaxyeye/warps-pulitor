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
package org.warps.monitor.jobs.core;

import org.apache.hadoop.mapreduce.Reducer;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.monitor.common.PulsarContext;

import java.io.IOException;

/**
 * Created by vincent on 16-9-24.
 * Copyright @ 2013-2016 Warpspeed Information. All rights reserved
 */
public class ReducerContext<KEYIN, VALUEIN, KEYOUT, VALUEOUT> implements PulsarContext<KEYIN, VALUEIN, KEYOUT, VALUEOUT> {

  private Reducer<KEYIN, VALUEIN, KEYOUT, VALUEOUT>.Context context;

  public ReducerContext() {
  }

  public ReducerContext(Reducer<KEYIN, VALUEIN, KEYOUT, VALUEOUT>.Context context) {
    this.context = context;
  }

  public void setContext(Reducer<KEYIN, VALUEIN, KEYOUT, VALUEOUT>.Context context) {
    this.context = context;
  }
  
  @Override
  public PulsarConfiguration getConfiguration() {
    // Wrap configuration
    return new PulsarConfiguration(context.getConfiguration());
  }

  @Override
  public boolean nextKey() throws IOException, InterruptedException {
    return context.nextKey();
  }

  @Override
  public boolean nextKeyValue() throws IOException, InterruptedException {
    return context.nextKeyValue();
  }

  @Override
  public KEYIN getCurrentKey() throws IOException, InterruptedException {
    return context.getCurrentKey();
  }

  @Override
  public VALUEIN getCurrentValue() throws IOException, InterruptedException {
    return context.getCurrentValue();
  }

  @Override
  public void write(KEYOUT var1, VALUEOUT var2) throws IOException, InterruptedException {
    context.write(var1, var2);
  }

  @Override
  public Iterable<VALUEIN> getValues() throws IOException, InterruptedException {
    return context.getValues();
  }

  @Override
  public void setStatus(String var1) {
    context.setStatus(var1);
  }

  @Override
  public String getStatus() {
    return context.getStatus();
  }

  @Override
  public int getJobId() {
    return context.getJobID().getId();
  }

  @Override
  public String getJobName() { return context.getJobName(); }
}
