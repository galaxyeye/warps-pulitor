package org.warps.monitor.common.options;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import org.warps.pulsar.common.Params;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by vincent on 17-3-18.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class EntityOptions extends PulsarOptions {
  @Parameter(names = {"-e", "--entity"}, description = "The entity name.")
  private String name;
  @Parameter(names = {"-ed", "--entity-dom"}, description = "The entity's container path.")
  private String path = "body";
  @DynamicParameter(names = {"-F"}, description = "Pulsar field extractors to extract entity fields")
  private Map<String, String> fields = new HashMap<>();

  private CollectionOptions collectionOptions = new CollectionOptions();

  public EntityOptions() {
  }

  /**
   * Since space can not appear in dynamic parameters in command line, we use % instead
   * */
  public void afterInject() {
    fields = fields.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().replaceAll("%", " "), (e1, e2) -> e1));

    collectionOptions.afterInject();
  }

  public String getName() { return name; }

  public String getPath() { return path; }

  public Map<String, String> getFields() { return fields; }

  public CollectionOptions getCollectionOptions() { return collectionOptions; }

  public Params getParams() {
    Map<String, Object> fieldsParams = fields.entrySet().stream()
        .map(e -> "-F" + e.getKey() + "=" + e.getValue())
        .collect(Collectors.toMap(Object::toString, v -> ""));

    return Params.of(
        "-e", name,
        "-ed", path
    )
        .filter(p -> p.getValue() != null)
        .filter(p -> !p.getValue().toString().isEmpty())
        .merge(Params.of(fieldsParams))
        .merge(collectionOptions.getParams());
  }

  @Override
  public String toString() {
    return getParams().withKVDelimiter(" ").formatAsLine().replaceAll("\\s+", " ");
  }
}
