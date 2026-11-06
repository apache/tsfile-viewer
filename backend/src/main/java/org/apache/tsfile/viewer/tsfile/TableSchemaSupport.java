/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.tsfile.viewer.tsfile;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.read.v4.ITsFileReader;

/**
 * Reads the table schemas of a V4 TsFile without failing on tree model files.
 *
 * <p>Callers use an empty result to decide that the file is a tree model file and to fall back to
 * {@link org.apache.tsfile.read.TsFileSequenceReader}. {@code ITsFileReader.getAllTableSchema()}
 * does not honour that contract: on a tree model file the underlying {@code
 * DeviceTableModelReader} dereferences a null table schema map and throws {@link
 * NullPointerException} instead of returning an empty list.
 */
public final class TableSchemaSupport {

  private static final Logger logger = LoggerFactory.getLogger(TableSchemaSupport.class);

  private TableSchemaSupport() {}

  /**
   * Returns the table schemas of the file, or an empty list when the file carries no table schema.
   *
   * @param reader open V4 TsFile reader
   * @return table schemas, never null
   * @throws IOException if the metadata cannot be read
   */
  public static List<TableSchema> readAll(ITsFileReader reader) throws IOException {
    try {
      List<TableSchema> schemas = reader.getAllTableSchema();
      return schemas == null ? List.of() : schemas;
    } catch (NullPointerException e) {
      // tsfile 2.x throws NPE here for tree model files instead of returning null or an empty list.
      logger.debug("File carries no table schema (tree model): {}", e.getMessage());
      return List.of();
    }
  }
}
