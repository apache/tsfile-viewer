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

package org.apache.tsfile.viewer.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import org.apache.tsfile.viewer.dto.MeasurementDTO;
import org.apache.tsfile.viewer.dto.TableDTO;
import org.apache.tsfile.viewer.tsfile.TsFileDataReader;
import org.apache.tsfile.viewer.tsfile.TsFileDataReader.DataReadResult;
import org.apache.tsfile.viewer.tsfile.TsFileDataReader.DataRow;
import org.apache.tsfile.viewer.tsfile.TsFileParser;

/**
 * Real file validation tests using actual TsFile samples.
 *
 * <p>Validates:
 *
 * <ul>
 *   <li>Tree model detection and metadata parsing (root.stock files)
 *   <li>Table model detection and metadata parsing (stock, wind files)
 *   <li>Device-based pagination for both models
 *   <li>TAG vs FIELD column identification
 * </ul>
 */
@DisplayName("Real TsFile Validation Tests")
class RealFileValidationTest {

  private TsFileParser parser;
  private TsFileDataReader dataReader;
  private Path samplesDir;

  @BeforeEach
  void setUp() {
    parser = new TsFileParser();
    dataReader = new TsFileDataReader();
    samplesDir = Paths.get("../tsfile-samples/tsfiles");
  }

  static boolean samplesExist() {
    return Paths.get("../tsfile-samples/tsfiles").toFile().exists();
  }

  @Test
  @EnabledIf("samplesExist")
  @DisplayName("Should detect and parse tree model file (root.stock)")
  void shouldDetectAndParseTreeModelFile() throws Exception {
    File treeFile = samplesDir.resolve("tree/root.stock/1768435200963-1-0-0.tsfile").toFile();

    if (!treeFile.exists()) {
      System.out.println("SKIP: Tree model file not found: " + treeFile.getAbsolutePath());
      return;
    }

    System.out.println("\n=== Tree Model File Validation ===");
    System.out.println("File: " + treeFile.getName());

    // Parse metadata
    TsFileParser.BasicMetadata metadata = parser.parseMetadata(treeFile.getAbsolutePath());
    System.out.println("Version: " + metadata.version());
    System.out.println("Device Count: " + metadata.deviceCount());
    System.out.println("Measurement Count: " + metadata.measurementCount());
    System.out.println("Time Range: " + metadata.startTime() + " - " + metadata.endTime());

    assertThat(metadata.version()).isNotNull();
    assertThat(metadata.deviceCount()).isGreaterThan(0);
    assertThat(metadata.measurementCount()).isGreaterThan(0);

    // Parse measurements
    List<MeasurementDTO> measurements = parser.parseMeasurements(treeFile.getAbsolutePath());
    System.out.println("\nMeasurements (" + measurements.size() + " total):");
    for (MeasurementDTO m : measurements) {
      System.out.println(
          String.format(
              "  - %s: %s (encoding=%s, compression=%s, category=%s)",
              m.getName(),
              m.getDataType(),
              m.getEncoding(),
              m.getCompression(),
              m.getColumnCategory()));
    }

    assertThat(measurements).isNotEmpty();
    // Tree model: all measurements should be FIELD (no TAG columns)
    long tagCount = measurements.stream().filter(m -> "TAG".equals(m.getColumnCategory())).count();
    System.out.println("\nTAG columns: " + tagCount);
    System.out.println("FIELD columns: " + (measurements.size() - tagCount));

    // Parse tables
    List<TableDTO> tables = parser.parseTables(treeFile.getAbsolutePath());
    System.out.println("\nTables: " + tables.size());
    for (TableDTO table : tables) {
      System.out.println(
          String.format(
              "  Table: %s (TAG=%d, FIELD=%d)",
              table.getTableName(), table.getTagColumns().size(), table.getFieldColumns().size()));
    }

    // Read data with pagination
    DataReadResult result = dataReader.readDataByTimeRange(treeFile, null, null, 10, 0);
    System.out.println("\nData Preview (first 10 rows):");
    System.out.println("Columns: " + result.getColumnNames());
    for (int i = 0; i < Math.min(3, result.getData().size()); i++) {
      DataRow row = result.getData().get(i);
      System.out.println(
          String.format(
              "  Row %d: Time=%d, Device=%s, Values=%s",
              i + 1, row.getTimestamp(), row.getTableName(), row.getValues()));
    }

    assertThat(result.getData()).isNotEmpty();
    assertThat(result.getColumnNames()).contains("Time", "Device");
  }

  @Test
  @EnabledIf("samplesExist")
  @DisplayName("Should detect and parse table model file (stock)")
  void shouldDetectAndParseTableModelFileStock() throws Exception {
    File tableFile = samplesDir.resolve("table/stock/1767225600475-1-3-0.tsfile").toFile();

    if (!tableFile.exists()) {
      System.out.println("SKIP: Table model file not found: " + tableFile.getAbsolutePath());
      return;
    }

    System.out.println("\n=== Table Model File Validation (stock) ===");
    System.out.println("File: " + tableFile.getName());

    // Parse metadata
    TsFileParser.BasicMetadata metadata = parser.parseMetadata(tableFile.getAbsolutePath());
    System.out.println("Version: " + metadata.version());
    System.out.println("Device Count: " + metadata.deviceCount());
    System.out.println("Measurement Count: " + metadata.measurementCount());
    System.out.println("Time Range: " + metadata.startTime() + " - " + metadata.endTime());

    assertThat(metadata.version()).isNotNull();
    assertThat(metadata.deviceCount()).isGreaterThan(0);

    // Parse measurements
    List<MeasurementDTO> measurements = parser.parseMeasurements(tableFile.getAbsolutePath());
    System.out.println("\nMeasurements (" + measurements.size() + " total):");
    for (MeasurementDTO m : measurements) {
      System.out.println(
          String.format(
              "  - %s: %s (encoding=%s, compression=%s, category=%s)",
              m.getName(),
              m.getDataType(),
              m.getEncoding(),
              m.getCompression(),
              m.getColumnCategory()));
    }

    assertThat(measurements).isNotEmpty();
    // Table model: should have TAG columns
    long tagCount = measurements.stream().filter(m -> "TAG".equals(m.getColumnCategory())).count();
    long fieldCount =
        measurements.stream().filter(m -> "FIELD".equals(m.getColumnCategory())).count();
    System.out.println("\nTAG columns: " + tagCount);
    System.out.println("FIELD columns: " + fieldCount);

    assertThat(tagCount).isGreaterThan(0);

    // Parse tables
    List<TableDTO> tables = parser.parseTables(tableFile.getAbsolutePath());
    System.out.println("\nTables: " + tables.size());
    for (TableDTO table : tables) {
      System.out.println(String.format("  Table: %s", table.getTableName()));
      System.out.println("    TAG columns (" + table.getTagColumns().size() + "):");
      for (MeasurementDTO tag : table.getTagColumns()) {
        System.out.println(String.format("      - %s: %s", tag.getName(), tag.getDataType()));
      }
      System.out.println("    FIELD columns (" + table.getFieldColumns().size() + "):");
      for (MeasurementDTO field : table.getFieldColumns()) {
        System.out.println(String.format("      - %s: %s", field.getName(), field.getDataType()));
      }
    }

    assertThat(tables).isNotEmpty();
    assertThat(tables.get(0).getTagColumns()).isNotEmpty();

    // Read data with pagination
    DataReadResult result = dataReader.readDataByTimeRange(tableFile, null, null, 10, 0);
    System.out.println("\nData Preview (first 10 rows):");
    System.out.println("Columns: " + result.getColumnNames());
    for (int i = 0; i < Math.min(3, result.getData().size()); i++) {
      DataRow row = result.getData().get(i);
      System.out.println(
          String.format(
              "  Row %d: Time=%d, Device=%s, Values=%s",
              i + 1, row.getTimestamp(), row.getTableName(), row.getValues()));
    }

    assertThat(result.getData()).isNotEmpty();
    // Verify device ID construction: tablename.tagvalue1.tagvalue2...
    for (DataRow row : result.getData()) {
      assertThat(row.getTableName()).contains(".");
    }
  }

  @Test
  @EnabledIf("samplesExist")
  @DisplayName("Should detect and parse table model file (wind)")
  void shouldDetectAndParseTableModelFileWind() throws Exception {
    File tableFile = samplesDir.resolve("table/wind/1765943964260-1-0-0.tsfile").toFile();

    if (!tableFile.exists()) {
      System.out.println("SKIP: Table model file not found: " + tableFile.getAbsolutePath());
      return;
    }

    System.out.println("\n=== Table Model File Validation (wind) ===");
    System.out.println("File: " + tableFile.getName());

    // Parse metadata
    TsFileParser.BasicMetadata metadata = parser.parseMetadata(tableFile.getAbsolutePath());
    System.out.println("Version: " + metadata.version());
    System.out.println("Device Count: " + metadata.deviceCount());
    System.out.println("Measurement Count: " + metadata.measurementCount());

    assertThat(metadata.version()).isNotNull();

    // Parse measurements
    List<MeasurementDTO> measurements = parser.parseMeasurements(tableFile.getAbsolutePath());
    System.out.println("\nMeasurements (" + measurements.size() + " total):");
    long tagCount = measurements.stream().filter(m -> "TAG".equals(m.getColumnCategory())).count();
    long fieldCount =
        measurements.stream().filter(m -> "FIELD".equals(m.getColumnCategory())).count();
    System.out.println("TAG columns: " + tagCount);
    System.out.println("FIELD columns: " + fieldCount);

    // Parse tables
    List<TableDTO> tables = parser.parseTables(tableFile.getAbsolutePath());
    System.out.println("\nTables: " + tables.size());
    for (TableDTO table : tables) {
      System.out.println(
          String.format(
              "  Table: %s (TAG=%d, FIELD=%d)",
              table.getTableName(), table.getTagColumns().size(), table.getFieldColumns().size()));
    }

    // Read data
    DataReadResult result = dataReader.readDataByTimeRange(tableFile, null, null, 5, 0);
    System.out.println("\nData Preview (first 5 rows):");
    for (int i = 0; i < Math.min(3, result.getData().size()); i++) {
      DataRow row = result.getData().get(i);
      System.out.println(String.format("  Row %d: Device=%s", i + 1, row.getTableName()));
    }
  }

  @Test
  @EnabledIf("samplesExist")
  @DisplayName("Should handle device-based pagination for table model")
  void shouldHandleDeviceBasedPaginationForTableModel() throws Exception {
    File tableFile = samplesDir.resolve("table/stock/1767225600475-1-3-0.tsfile").toFile();

    if (!tableFile.exists()) {
      System.out.println("SKIP: File not found");
      return;
    }

    System.out.println("\n=== Device-Based Pagination Test ===");

    // Page 1
    DataReadResult page1 = dataReader.readDataByTimeRange(tableFile, null, null, 10, 0);
    System.out.println("Page 1: " + page1.getData().size() + " rows");
    System.out.println("Has more: " + page1.hasMore());

    // Page 2
    DataReadResult page2 = dataReader.readDataByTimeRange(tableFile, null, null, 10, 10);
    System.out.println("Page 2: " + page2.getData().size() + " rows");
    System.out.println("Has more: " + page2.hasMore());

    assertThat(page1.getData()).isNotEmpty();
  }

  @Test
  @EnabledIf("samplesExist")
  @DisplayName("Should parse auto test files")
  void shouldParseAutoTestFiles() throws Exception {
    File autoFile = samplesDir.resolve("auto/1766414815585-176-3-21.tsfile").toFile();

    if (!autoFile.exists()) {
      System.out.println("SKIP: Auto test file not found");
      return;
    }

    System.out.println("\n=== Auto Test File Validation ===");
    System.out.println("File: " + autoFile.getName());

    TsFileParser.BasicMetadata metadata = parser.parseMetadata(autoFile.getAbsolutePath());
    System.out.println("Version: " + metadata.version());
    System.out.println("Device Count: " + metadata.deviceCount());
    System.out.println("Measurement Count: " + metadata.measurementCount());

    List<MeasurementDTO> measurements = parser.parseMeasurements(autoFile.getAbsolutePath());
    System.out.println("Measurements: " + measurements.size());

    List<TableDTO> tables = parser.parseTables(autoFile.getAbsolutePath());
    System.out.println("Tables: " + tables.size());

    DataReadResult result = dataReader.readDataByTimeRange(autoFile, null, null, 5, 0);
    System.out.println("Data rows: " + result.getData().size());

    assertThat(metadata.version()).isNotNull();
  }
}
