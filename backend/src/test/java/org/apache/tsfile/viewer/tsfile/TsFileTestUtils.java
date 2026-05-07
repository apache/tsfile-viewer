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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.exception.write.WriteProcessException;
import org.apache.tsfile.file.metadata.ColumnSchemaBuilder;
import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.write.record.Tablet;
import org.apache.tsfile.write.v4.ITsFileWriter;
import org.apache.tsfile.write.v4.TsFileWriterBuilder;

/**
 * Utility class for generating test TsFile files with various models.
 *
 * <p>Supports creation of:
 *
 * <ul>
 *   <li>Table Model files (V4 with TAG and FIELD columns)
 *   <li>Tree Model files (traditional device-measurement structure)
 *   <li>Multi-table files
 *   <li>Files with different data patterns
 * </ul>
 */
public class TsFileTestUtils {

  /**
   * Creates a table model TsFile with a single table and single device (identified by TAG columns).
   *
   * @param outputPath the output file path
   * @param tableName the table name
   * @param deviceId the device ID (stored in tag1 column)
   * @param rowCount the number of rows to write
   * @return the created file
   * @throws IOException if file operations fail
   * @throws WriteProcessException if write fails
   */
  public static File createTableModelSingleDevice(
      Path outputPath, String tableName, String deviceId, int rowCount)
      throws IOException, WriteProcessException {
    Files.deleteIfExists(outputPath);
    File file = outputPath.toFile();

    TableSchema tableSchema =
        new TableSchema(
            tableName,
            Arrays.asList(
                new ColumnSchemaBuilder()
                    .name("device_id")
                    .dataType(TSDataType.STRING)
                    .category(ColumnCategory.TAG)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("temperature")
                    .dataType(TSDataType.FLOAT)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("humidity")
                    .dataType(TSDataType.INT32)
                    .category(ColumnCategory.FIELD)
                    .build()));

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(file).tableSchema(tableSchema).build()) {
      Tablet tablet =
          new Tablet(
              Arrays.asList("device_id", "temperature", "humidity"),
              Arrays.asList(TSDataType.STRING, TSDataType.FLOAT, TSDataType.INT32));

      for (int i = 0; i < rowCount; i++) {
        tablet.addTimestamp(i, i * 1000L);
        tablet.addValue(i, "device_id", deviceId);
        tablet.addValue(i, "temperature", 20.0f + i * 0.5f);
        tablet.addValue(i, "humidity", 50 + i);
      }

      writer.write(tablet);
    }

    return file;
  }

  /**
   * Creates a table model TsFile with a single table but multiple devices (distinguished by
   * different TAG column values).
   *
   * @param outputPath the output file path
   * @param tableName the table name
   * @param deviceIds list of device IDs
   * @param rowsPerDevice number of rows per device
   * @return the created file
   * @throws IOException if file operations fail
   * @throws WriteProcessException if write fails
   */
  public static File createTableModelMultipleDevices(
      Path outputPath, String tableName, List<String> deviceIds, int rowsPerDevice)
      throws IOException, WriteProcessException {
    Files.deleteIfExists(outputPath);
    File file = outputPath.toFile();

    TableSchema tableSchema =
        new TableSchema(
            tableName,
            Arrays.asList(
                new ColumnSchemaBuilder()
                    .name("device_id")
                    .dataType(TSDataType.STRING)
                    .category(ColumnCategory.TAG)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("location")
                    .dataType(TSDataType.STRING)
                    .category(ColumnCategory.TAG)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("temperature")
                    .dataType(TSDataType.FLOAT)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("humidity")
                    .dataType(TSDataType.INT32)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("pressure")
                    .dataType(TSDataType.DOUBLE)
                    .category(ColumnCategory.FIELD)
                    .build()));

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(file).tableSchema(tableSchema).build()) {
      Tablet tablet =
          new Tablet(
              Arrays.asList("device_id", "location", "temperature", "humidity", "pressure"),
              Arrays.asList(
                  TSDataType.STRING,
                  TSDataType.STRING,
                  TSDataType.FLOAT,
                  TSDataType.INT32,
                  TSDataType.DOUBLE));

      for (int deviceIdx = 0; deviceIdx < deviceIds.size(); deviceIdx++) {
        String deviceId = deviceIds.get(deviceIdx);
        String location = "location_" + deviceIdx;

        for (int i = 0; i < rowsPerDevice; i++) {
          int row = tablet.getRowSize();
          long timestamp = (deviceIdx * rowsPerDevice + i) * 1000L;
          tablet.addTimestamp(row, timestamp);
          tablet.addValue(row, "device_id", deviceId);
          tablet.addValue(row, "location", location);
          tablet.addValue(row, "temperature", 20.0f + deviceIdx * 10 + i * 0.5f);
          tablet.addValue(row, "humidity", 50 + deviceIdx * 10 + i);
          tablet.addValue(row, "pressure", 1000.0 + deviceIdx * 5 + i * 0.1);
        }
      }

      writer.write(tablet);
    }

    return file;
  }

  /**
   * Creates a table model TsFile with a single table that can simulate multiple tables.
   *
   * <p>Note: TsFile V4 ITsFileWriter doesn't expose multi-table write API yet. This creates a
   * single table with different device_ids to simulate multiple table scenarios for testing.
   *
   * @param outputPath the output file path
   * @param tableNames list of "table names" (will be stored in device_id TAG)
   * @param rowsPerTable number of rows per "table"
   * @return the created file
   * @throws IOException if file operations fail
   * @throws WriteProcessException if write fails
   */
  public static File createTableModelMultipleTables(
      Path outputPath, List<String> tableNames, int rowsPerTable)
      throws IOException, WriteProcessException {
    Files.deleteIfExists(outputPath);
    File file = outputPath.toFile();

    // Create single table schema with device_id as TAG to distinguish "tables"
    TableSchema tableSchema =
        new TableSchema(
            "multi_table_data",
            Arrays.asList(
                new ColumnSchemaBuilder()
                    .name("table_name")
                    .dataType(TSDataType.STRING)
                    .category(ColumnCategory.TAG)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("temperature")
                    .dataType(TSDataType.FLOAT)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("humidity")
                    .dataType(TSDataType.INT32)
                    .category(ColumnCategory.FIELD)
                    .build()));

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(file).tableSchema(tableSchema).build()) {

      Tablet tablet =
          new Tablet(
              Arrays.asList("table_name", "temperature", "humidity"),
              Arrays.asList(TSDataType.STRING, TSDataType.FLOAT, TSDataType.INT32));

      // Write data for each "table" by using different tag values
      for (int tableIdx = 0; tableIdx < tableNames.size(); tableIdx++) {
        String tableName = tableNames.get(tableIdx);

        for (int i = 0; i < rowsPerTable; i++) {
          int row = tablet.getRowSize();
          long timestamp = (tableIdx * rowsPerTable + i) * 1000L;
          tablet.addTimestamp(row, timestamp);
          tablet.addValue(row, "table_name", tableName);
          tablet.addValue(row, "temperature", 20.0f + tableIdx * 10 + i * 0.5f);
          tablet.addValue(row, "humidity", 50 + tableIdx * 10 + i);
        }
      }

      writer.write(tablet);
    }

    return file;
  }

  /**
   * Creates a table model TsFile with data designed for aggregation and visualization testing.
   *
   * @param outputPath the output file path
   * @param tableName the table name
   * @param deviceId the device ID
   * @param startTime start timestamp in milliseconds
   * @param interval interval between data points in milliseconds
   * @param pointCount number of data points
   * @return the created file
   * @throws IOException if file operations fail
   * @throws WriteProcessException if write fails
   */
  public static File createTableModelForVisualization(
      Path outputPath,
      String tableName,
      String deviceId,
      long startTime,
      long interval,
      int pointCount)
      throws IOException, WriteProcessException {
    Files.deleteIfExists(outputPath);
    File file = outputPath.toFile();

    TableSchema tableSchema =
        new TableSchema(
            tableName,
            Arrays.asList(
                new ColumnSchemaBuilder()
                    .name("device_id")
                    .dataType(TSDataType.STRING)
                    .category(ColumnCategory.TAG)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("temperature")
                    .dataType(TSDataType.FLOAT)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("humidity")
                    .dataType(TSDataType.INT32)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("pressure")
                    .dataType(TSDataType.DOUBLE)
                    .category(ColumnCategory.FIELD)
                    .build()));

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(file).tableSchema(tableSchema).build()) {
      Tablet tablet =
          new Tablet(
              Arrays.asList("device_id", "temperature", "humidity", "pressure"),
              Arrays.asList(
                  TSDataType.STRING, TSDataType.FLOAT, TSDataType.INT32, TSDataType.DOUBLE));

      for (int i = 0; i < pointCount; i++) {
        long timestamp = startTime + i * interval;
        // Create sinusoidal patterns for testing visualization
        double angle = 2 * Math.PI * i / 100.0;
        float temperature = (float) (20.0 + 5.0 * Math.sin(angle));
        int humidity = (int) (60 + 20 * Math.cos(angle));
        double pressure = 1000.0 + 10.0 * Math.sin(2 * angle);

        tablet.addTimestamp(i, timestamp);
        tablet.addValue(i, "device_id", deviceId);
        tablet.addValue(i, "temperature", temperature);
        tablet.addValue(i, "humidity", humidity);
        tablet.addValue(i, "pressure", pressure);
      }

      writer.write(tablet);
    }

    return file;
  }

  /**
   * Creates a tree model TsFile (traditional aligned timeseries format).
   *
   * <p>Note: TsFile V4 primarily uses table model. This method creates a simple table that can be
   * queried similar to tree model for compatibility testing.
   *
   * @param outputPath the output file path
   * @param deviceName the device name (used as table name)
   * @param rowCount the number of rows to write
   * @return the created file
   * @throws IOException if file operations fail
   * @throws WriteProcessException if write fails
   */
  public static File createTreeModelFile(Path outputPath, String deviceName, int rowCount)
      throws IOException, WriteProcessException {
    Files.deleteIfExists(outputPath);
    File file = outputPath.toFile();

    // In V4, we simulate tree model using table model without TAG columns
    TableSchema tableSchema =
        new TableSchema(
            deviceName,
            Arrays.asList(
                new ColumnSchemaBuilder()
                    .name("s1")
                    .dataType(TSDataType.FLOAT)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("s2")
                    .dataType(TSDataType.INT32)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("s3")
                    .dataType(TSDataType.DOUBLE)
                    .category(ColumnCategory.FIELD)
                    .build()));

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(file).tableSchema(tableSchema).build()) {
      Tablet tablet =
          new Tablet(
              Arrays.asList("s1", "s2", "s3"),
              Arrays.asList(TSDataType.FLOAT, TSDataType.INT32, TSDataType.DOUBLE));

      for (int i = 0; i < rowCount; i++) {
        tablet.addTimestamp(i, i * 1000L);
        tablet.addValue(i, "s1", 10.0f + i * 0.1f);
        tablet.addValue(i, "s2", 100 + i);
        tablet.addValue(i, "s3", 1000.0 + i * 0.5);
      }

      writer.write(tablet);
    }

    return file;
  }

  /**
   * Creates a tree model TsFile with multiple "devices" simulated in a single table.
   *
   * <p>Note: TsFile V4 ITsFileWriter doesn't expose multi-table write API yet. This creates a
   * single table with device_name column to distinguish devices for testing tree model scenarios.
   *
   * @param outputPath the output file path
   * @param deviceNames list of device names
   * @param rowsPerDevice number of rows per device
   * @return the created file
   * @throws IOException if file operations fail
   * @throws WriteProcessException if write fails
   */
  public static File createTreeModelMultipleDevices(
      Path outputPath, List<String> deviceNames, int rowsPerDevice)
      throws IOException, WriteProcessException {
    Files.deleteIfExists(outputPath);
    File file = outputPath.toFile();

    // Create table schema without TAG columns (simulates tree model)
    // Add device_name as a FIELD to distinguish devices
    TableSchema schema =
        new TableSchema(
            "tree_model_data",
            Arrays.asList(
                new ColumnSchemaBuilder()
                    .name("device_name")
                    .dataType(TSDataType.STRING)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("temperature")
                    .dataType(TSDataType.FLOAT)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("humidity")
                    .dataType(TSDataType.INT32)
                    .category(ColumnCategory.FIELD)
                    .build()));

    try (ITsFileWriter writer = new TsFileWriterBuilder().file(file).tableSchema(schema).build()) {

      Tablet tablet =
          new Tablet(
              Arrays.asList("device_name", "temperature", "humidity"),
              Arrays.asList(TSDataType.STRING, TSDataType.FLOAT, TSDataType.INT32));

      // Write data for all devices
      for (int deviceIdx = 0; deviceIdx < deviceNames.size(); deviceIdx++) {
        String deviceName = deviceNames.get(deviceIdx);

        for (int i = 0; i < rowsPerDevice; i++) {
          int row = tablet.getRowSize();
          long timestamp = (deviceIdx * rowsPerDevice + i) * 1000L;
          tablet.addTimestamp(row, timestamp);
          tablet.addValue(row, "device_name", deviceName);
          tablet.addValue(row, "temperature", 20.0f + deviceIdx * 5 + i * 0.5f);
          tablet.addValue(row, "humidity", 50 + deviceIdx * 10 + i);
        }
      }

      writer.write(tablet);
    }

    return file;
  }
}
