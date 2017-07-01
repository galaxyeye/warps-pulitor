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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.pulsar.common.PulsarConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Created by vincent on 17-3-23.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class PulsarFiles {

  public static final Logger LOG = LoggerFactory.getLogger(PulsarFiles.class);

  public static final String PATH_PULITOR_TMP_DIR = "/tmp/pulitor-" + System.getenv("USER");
  public static final String PATH_LAST_BATCH_ID = PATH_PULITOR_TMP_DIR + "/last-batch-id";

  public PulsarFiles(PulsarConfiguration conf) {
  }

  public static Path writeBatchId(String batchId) throws IOException {
    if (batchId != null && !batchId.isEmpty()) {
      Path path = Paths.get(PATH_LAST_BATCH_ID);
      Files.write(path, (batchId + "\n").getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      return path;
    }

    return null;
  }

  public static String readBatchIdOrDefault(String defaultValue) {
    try {
      return Files.readAllLines(Paths.get(PATH_LAST_BATCH_ID)).get(0);
    } catch (Throwable ignored) {}

    return defaultValue;
  }
}
