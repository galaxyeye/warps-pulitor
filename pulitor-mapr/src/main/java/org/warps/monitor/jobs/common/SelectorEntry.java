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
package org.warps.monitor.jobs.common;

import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by vincent on 17-4-11.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class SelectorEntry implements WritableComparable<SelectorEntry> {

  public static final Logger LOG = LoggerFactory.getLogger(SelectorEntry.class);

  public static class SelectorEntryComparator extends WritableComparator {
    public SelectorEntryComparator() {
      super(SelectorEntry.class, true);
    }
  }

  static {
    WritableComparator.define(SelectorEntry.class, new SelectorEntryComparator());
  }

  private String url;
  private float score;

  public SelectorEntry() {

  }

  public SelectorEntry(String url, float score) {
    this.url = url;
    this.score = score;
  }

  public String getUrl() {
    return url;
  }

  public float getScore() {
    return score;
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    url = Text.readString(in);
    FloatWritable scoreVectorWritable = new FloatWritable(score);
    scoreVectorWritable.readFields(in);
    score = scoreVectorWritable.get();

//    LOG.info(url);
//    LOG.info("readFields : " + score.toString());
  }

  @Override
  public void write(DataOutput out) throws IOException {
    Text.writeString(out, url);
    new FloatWritable(score).write(out);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + url.hashCode();
    result = prime * result + (int)score;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SelectorEntry)) {
      return false;
    }

    SelectorEntry other = (SelectorEntry) obj;
    return compareTo(other) == 0;
  }

  /**
   * Items with higher score comes first in reduce phrase
   */
  @Override
  public int compareTo(SelectorEntry se) {
    int comp = -(int)(score - se.score);
    return comp != 0 ? comp : url.compareTo(se.url);
  }
}
