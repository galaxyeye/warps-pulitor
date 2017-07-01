/*
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
package org.warps.monitor.crawl.index;

import com.google.common.collect.Lists;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.pulsar.common.DateTimeUtil;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.PulsarConfiguration;
import org.warps.pulsar.persist.gora.db.WebDb;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import static org.warps.pulsar.common.PulsarConfiguration.*;

public class SolrIndexWriter {

  public static final Logger LOG = LoggerFactory.getLogger(SolrIndexWriter.class);
  public static final String INDEXER_PARAMS = "index.additional.params";

  private PulsarConfiguration conf;
  private String[] solrUrls = ArrayUtils.EMPTY_STRING_ARRAY;
  private String[] zkHosts = ArrayUtils.EMPTY_STRING_ARRAY;
  private String collection;
  private List<SolrClient> solrClients;
  private IndexerMapping indexerMapping;
  private ModifiableSolrParams solrParams;
  private WebDb webDb;

  private boolean isActive = false;
  private int totalAdds = 0;

  private final List<SolrInputDocument> inputDocs = new ArrayList<>();

  public SolrIndexWriter(IndexerMapping indexerMapping, PulsarConfiguration conf) {
    this.indexerMapping = indexerMapping;

    reload(conf);
  }

  public PulsarConfiguration getConf() {
    return conf;
  }

  public void reload(PulsarConfiguration conf) {
    this.conf = conf;

    solrUrls = conf.getStrings(INDEXER_URL, ArrayUtils.EMPTY_STRING_ARRAY);
    zkHosts = conf.getStrings(INDEXER_ZK, ArrayUtils.EMPTY_STRING_ARRAY);
    collection = conf.get(INDEXER_COLLECTION);

    if (solrUrls == null && zkHosts == null) {
      String message = "Either Zookeeper URL or SOLR URL is required";
      LOG.error(message);
      throw new RuntimeException("Failed to init SolrIndexWriter");
    }

    solrParams = new ModifiableSolrParams();
    conf.getKvs(INDEXER_PARAMS).forEach((key, value) -> solrParams.add(key, value));

    LOG.info(getParams().format());
  }

  public Params getParams() {
    return Params.of(
        "className", this.getClass().getSimpleName(),
        "solrParams", solrParams,
        "zkHosts", StringUtils.join(zkHosts, ", "),
        "solrUrls", StringUtils.join(solrUrls, ", "),
        "collection", collection
    );
  }

  public WebDb getWebDb() {
    return webDb;
  }

  public void setWebDb(WebDb webDb) {
    this.webDb = webDb;
  }

  public boolean isActive() { return isActive; }

  public void open(Configuration conf) {
    solrClients = SolrUtils.getSolrClients(solrUrls, zkHosts, collection);
    isActive = true;
  }

  public void open(String solrUrl) {
    solrClients = Lists.newArrayList(SolrUtils.getSolrClient(solrUrl));
    isActive = true;
  }

  public void write(IndexDocument doc) throws IOException {
    final SolrInputDocument inputDoc = new SolrInputDocument();

    for (Entry<CharSequence, IndexField> e : doc.getFields().entrySet()) {
      String key = indexerMapping.mapKeyIfExists(e.getKey().toString());
      if (key == null) {
        continue;
      }

      float weight = e.getValue().getWeight();
      for (final Object field : e.getValue().getValues()) {
        // normalise the string representation for a Date
        Object val2 = convertIndexField(field);

        Boolean isMultiValued = indexerMapping.isMultiValued(e.getKey().toString());
        if (!isMultiValued) {
          if (inputDoc.getField(key) == null) {
            inputDoc.addField(key, val2, weight);
          }
        }
        else {
          inputDoc.addField(key, val2, weight);
        }
      } // for
    } // for

    inputDoc.setDocumentBoost(doc.getWeight());
    inputDocs.add(inputDoc);
    totalAdds++;

    if (inputDocs.size() >= 250) {
      push();
    }
  }

  private Object convertIndexField(Object field) {
    Object field2;
    if (field instanceof Date) {
      field2 = DateTimeUtil.isoInstantFormat((Date)field);
    }
    else if (field instanceof Instant) {
      field2 = DateTimeUtil.isoInstantFormat((Instant)field);
    }
    else if (field instanceof org.apache.avro.util.Utf8) {
      field2 = field.toString();
    }

    return field;
  }

  public void close() throws IOException {
    if (!isActive) {
      return;
    }

    commit();

    for (SolrClient solrClient : solrClients) {
      solrClient.close();
    }
    solrClients.clear();

    isActive = false;
  }

  public void commit() throws IOException {
    if (!isActive || inputDocs.isEmpty()) {
      return;
    }

    push();

    try {
      for (SolrClient solrClient : solrClients) {
        solrClient.commit();
      }
    } catch (SolrServerException|SolrException e) {
      LOG.error("Failed to write to solr " + e.toString());
      throw new IOException(e);
    }
  }

  public void push() throws IOException {
    if (inputDocs.size() > 0) {
      String message = "Indexing " + inputDocs.size() + "/" + totalAdds + " documents";
      LOG.info(message);

      UpdateRequest req = new UpdateRequest();
      req.add(inputDocs);
      req.setAction(AbstractUpdateRequest.ACTION.OPTIMIZE, false, false);
      req.setParams(solrParams);

      try {
        for (SolrClient solrClient : solrClients) {
          solrClient.request(req);
        }
      }
      catch (SolrServerException|SolrException e) {
        LOG.error("Failed to write to solr " + e.toString());
        throw new IOException(e);
      }
      finally {
        inputDocs.clear();
      }
    }
  }
}
