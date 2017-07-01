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
package org.warps.monitor.crawl.index;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.warps.pulsar.common.DateTimeUtil;
import org.warps.pulsar.common.UrlUtil;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** A {@link IndexDocument} is the unit of indexing. */
public class IndexDocument {

  private CharSequence key = "";
  private CharSequence url = "";
  private Map<CharSequence, IndexField> fields = new LinkedMap<>();
  private float weight = 1.0f;

  public IndexDocument() {
  }

  public IndexDocument(CharSequence key) {
    this.key = key;
    this.url = UrlUtil.unreverseUrl(key.toString());
  }

  public String getKey() {
    return key.toString();
  }

  public String getUrl() {
    return url.toString();
  }

  public void addIfAbsent(CharSequence name, Object value) {
    fields.computeIfAbsent(name, k -> new IndexField(value));
  }

  public void addIfNotEmpty(CharSequence name, String value) {
    if (value == null || value.isEmpty()) {
      return;
    }

    IndexField field = fields.get(name);
    if (field == null) {
      field = new IndexField(value);
      fields.put(name, field);
    } else {
      field.add(value);
    }
  }

  public void addIfNotNull(CharSequence name, Object value) {
    if (value == null) {
      return;
    }
    IndexField field = fields.get(name);
    if (field == null) {
      field = new IndexField(value);
      fields.put(name, field);
    } else {
      field.add(value);
    }
  }

  public void add(CharSequence name, Object value) {
    IndexField field = fields.get(name);
    if (field == null) {
      field = new IndexField(value);
      fields.put(name, field);
    } else {
      field.add(value);
    }
  }

  public Object getFieldValue(CharSequence name) {
    IndexField field = fields.get(name);
    if (field == null) {
      return null;
    }
    if (field.getValues().size() == 0) {
      return null;
    }
    return field.getValues().get(0);
  }

  public IndexField getField(CharSequence name) {
    return fields.get(name);
  }

  public IndexField removeField(CharSequence name) {
    return fields.remove(name);
  }

  public Collection<CharSequence> getFieldNames() {
    return fields.keySet();
  }

  public List<Object> getFieldValues(CharSequence name) {
    IndexField field = fields.get(name);
    if (field == null) {
      return null;
    }

    return field.getValues();
  }

  public String getFieldValueAsString(CharSequence name) {
    IndexField field = fields.get(name);
    if (field == null || field.getValues().isEmpty()) {
      return null;
    }

    return field.getValues().iterator().next().toString();
  }

  public float getWeight() {
    return weight;
  }

  public void setWeight(float weight) {
    this.weight = weight;
  }

  public Map<String, List<String>> asMultimap() {
    return fields.entrySet().stream()
        .map(e -> Pair.of(e.getKey().toString(), e.getValue().getStringValues()))
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
  }

  @Override
  public String toString() {
    String s = fields.entrySet().stream()
            .map(e -> "\t" + e.getKey() + ":\t" + e.getValue().toString())
            .collect(Collectors.joining("\n"));
    return "doc {\n" + s + "\n}\n";
  }

  public String formatAsLine() {
    String s = fields.entrySet().stream()
        .map(e -> "\t" + e.getKey() + ":\t" + e.getValue().toString())
            .map(l -> StringUtils.replaceChars(l, "[]", ""))
            .collect(Collectors.joining(", "));
    return s;
  }

  public Map<CharSequence, IndexField> getFields() { return fields; }

  private String format(Object obj) {
    if (obj instanceof Date) {
      return DateTimeUtil.isoInstantFormat((Date)obj);
    }
    else if (obj instanceof Instant) {
      return DateTimeUtil.isoInstantFormat((Instant)obj);
    }
    else {
      return obj.toString();
    }
  }
}
