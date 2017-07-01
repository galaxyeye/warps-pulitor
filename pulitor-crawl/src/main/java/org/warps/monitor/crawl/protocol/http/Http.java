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
package org.warps.monitor.crawl.protocol.http;

// JDK imports

import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.monitor.crawl.protocol.ProtocolException;
import org.warps.monitor.net.protocols.Response;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.pulsar.common.proxy.NoProxyException;
import org.warps.pulsar.persist.WebPage;

import java.io.IOException;
import java.net.URL;

public class Http extends HttpBase {

  public static final Logger LOG = LoggerFactory.getLogger(Http.class);

  public Http() {
    super(LOG);
  }

  public Http(Configuration conf) {
    super(LOG);
    setConf(conf);
  }

  @Override
  public void setConf(Configuration conf) {
    super.setConf(conf);
//     Level logLevel = Level.WARNING;
//     if (conf.getBoolean("http.verbose", false)) {
//     logLevel = Level.FINE;
//     }
//     LOG.setLevel(logLevel);
  }

  @Override
  protected Response getResponse(URL url, WebPage page, boolean redirect)
      throws ProtocolException, IOException, NoProxyException {
      Response r = null;
      try {
        r = new HttpResponse(this, url, page);
      } catch (InterruptedException e) {
        LOG.error(e.toString());
      }
      return r;
  }

  public static void main(String[] args) throws Exception {
    Http http = new Http();
    http.setConf(new PulsarConfiguration());
    main(http, args);
  }
}
