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

package org.warps.monitor.net.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.pulsar.common.StringUtil;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Storage class for <code>DomainSuffix</code> objects Note: this class is
 * singleton
 * 
 * @author Enis Soztutar &lt;enis.soz.pulsar@gmail.com&gt;
 */
public class DomainSuffixes {
  private static final Logger LOG = LoggerFactory.getLogger(DomainSuffixes.class);

  private static String configFile = "src/main/resources/domain-suffixes.xml";

  private HashMap<String, DomainSuffix> domains = new HashMap<>();

  private static DomainSuffixes instance;

  /**
   * private ctor
   */
  private DomainSuffixes() {
  }

  /**
   * Singleton instance, lazy instantination
   *
   * @return DomainSuffixes
   */
  public static DomainSuffixes getInstance() {
    if (instance == null) {
      LOG.info("Loading domain suffixes from resource " + configFile);
      try (InputStream input = DomainSuffixes.class.getClassLoader().getResourceAsStream(configFile)) {
        if (input == null) {
          LOG.error("Failed to load resource " + configFile);
        }
        else {
          instance = new DomainSuffixesReader().read(new DomainSuffixes(), input);
        }
      } catch (Exception e) {
        LOG.warn(StringUtil.stringifyException(e));
      }
    }

    return instance;
  }

  void addDomainSuffix(DomainSuffix tld) {
    domains.put(tld.getDomain(), tld);
  }

  /** return whether the extension is a registered domain entry */
  public boolean isDomainSuffix(String extension) {
    return domains.containsKey(extension);
  }

  /**
   * Return the {@link DomainSuffix} object for the extension, if extension is a
   * top level domain returned object will be an instance of
   * {@link TopLevelDomain}
   * 
   * @param extension
   *          of the domain
   */
  public DomainSuffix get(String extension) {
    return domains.get(extension);
  }
}
