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
public class CollectionOptions extends PulsarOptions {
  @Parameter(names = {"-c", "--collection"}, description = "The name of the collection")
  private String name;
  @Parameter(names = {"-cd", "--collection-dom"}, description = "The path of the collection")
  private String path;
  @Parameter(names = {"-ci", "--collection-item"}, description = "The path of entity fields")
  private String itemPath;
  @DynamicParameter(names = {"-FF"}, description = "Pulsar field extractors to extract sub entity fields")
  private Map<String, String> fields = new HashMap<>();

  public CollectionOptions() {
  }

  public CollectionOptions(String[] args) {
    super(args);
  }

  public void afterInject() {
    fields = fields.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().replaceAll("%", " "), (e1, e2) -> e1));
  }

  public String getName() { return name; }

  public String getPath() { return path; }

  public String getItemPath() { return itemPath; }

  public Map<String, String> getFields() { return fields; }

  public Params getParams() {
    Map<String, Object> fieldsParams = fields.entrySet().stream()
        .map(e -> "-FF" + e.getKey() + "=" + e.getValue())
        .collect(Collectors.toMap(Object::toString, v -> ""));

    return Params.of(
        "-c", name,
        "-cd", path,
        "-ci", itemPath
    )
        .filter(p -> p.getValue() != null)
        .filter(p -> !p.getValue().toString().isEmpty())
        .merge(Params.of(fieldsParams));
  }

  @Override
  public String toString() {
    return getParams().withKVDelimiter(" ").formatAsLine().replaceAll("\\s+", " ");
  }
}
