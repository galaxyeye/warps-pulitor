/**
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
 */
package org.warps.monitor.net.protocols;

// JDK imports
import org.warps.pulsar.common.HttpHeaders;
import org.warps.pulsar.persist.metadata.Metadata;

import java.net.URL;

// PulsarConstants imports

/**
 * A response interface. Makes all protocols model HTTP.
 */
public interface Response extends HttpHeaders {

  /** Returns the URL used to retrieve this response. */
  URL getUrl();

  /** Returns the response code. */
  int getCode();

  /** Returns the value of a named header. */
  String getHeader(String name);

  /** Returns all the headers. */
  Metadata getHeaders();

  /** Returns the full content of the response. */
  byte[] getContent();
}
