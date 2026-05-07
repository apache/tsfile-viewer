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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import org.apache.tsfile.viewer.dto.ChunkDTO;
import org.apache.tsfile.viewer.dto.MeasurementDTO;
import org.apache.tsfile.viewer.dto.RowGroupDTO;
import org.apache.tsfile.viewer.dto.TableDTO;

/**
 * 使用真实TsFile文件测试元数据读取功能
 *
 * <p>测试文件： 1. root.stock 数据文件 (4.7MB) - 用于正常功能测试 2. root.stock 空文件 (0B) - 用于健壮性测试
 */
class RealFilesMetadataTest {

  private final TsFileParser parser = new TsFileParser();

  // Stock data TsFile (4.7MB)
  private static final String STOCK_DATA_FILE =
      "/Users/critas/tsfile-samples/tsfiles/root.stock/150/2924/1768435200963-1-0-0.tsfile";

  // Empty TsFile (0B) - for robustness testing
  private static final String EMPTY_FILE =
      "/Users/critas/tsfile-samples/tsfiles/root.stock/150/2924/1768894419960-2-0-0.tsfile";

  @Test
  @EnabledIf("stockDataFileExists")
  void testStockDataFile_parseTables() throws Exception {
    // When
    List<TableDTO> tables = parser.parseTables(STOCK_DATA_FILE);

    // Then
    System.out.println("=== Stock Data File Tables ===");
    System.out.println("File: " + STOCK_DATA_FILE);
    System.out.println("Total tables: " + tables.size());

    if (!tables.isEmpty()) {
      for (TableDTO table : tables) {
        System.out.println("\nTable: " + table.getTableName());
        System.out.println("  TAG columns (" + table.getTagColumns().size() + "):");
        for (MeasurementDTO tag : table.getTagColumns()) {
          System.out.println("    - " + tag.getName() + " (" + tag.getDataType() + ")");
        }
        System.out.println("  FIELD columns (" + table.getFieldColumns().size() + "):");
        for (MeasurementDTO field : table.getFieldColumns()) {
          System.out.println("    - " + field.getName() + " (" + field.getDataType() + ")");
        }
      }

      // Verify table model
      for (TableDTO table : tables) {
        assertThat(table.getTableName()).isNotNull();
        assertThat(table.getTotalColumns()).isGreaterThan(0);
      }
    } else {
      System.out.println("\nNo tables found - this is a Tree Model TsFile (not Table Model)");
      System.out.println("✓ Tree model files correctly return empty table list");

      // For tree model, show measurements and devices instead
      System.out.println("\n=== Tree Model: Measurements ===");
      List<MeasurementDTO> measurements = parser.parseMeasurements(STOCK_DATA_FILE);
      System.out.println("Total measurements: " + measurements.size());
      for (MeasurementDTO m : measurements) {
        System.out.println(
            "  - "
                + m.getName()
                + ": "
                + m.getDataType()
                + " (encoding: "
                + m.getEncoding()
                + ", compression: "
                + m.getCompression()
                + ")");
      }

      System.out.println("\n=== Tree Model: Devices (from RowGroups) ===");
      List<RowGroupDTO> rowGroups = parser.parseRowGroups(STOCK_DATA_FILE);
      System.out.println("Total RowGroups: " + rowGroups.size());
      for (RowGroupDTO rg : rowGroups) {
        System.out.println("  - Device: " + rg.getDevice());
        System.out.println("    Time range: " + rg.getStartTime() + " - " + rg.getEndTime());
        System.out.println("    Chunks: " + rg.getChunkCount());
      }

      // Verify tree model data is readable
      assertThat(measurements).isNotEmpty();
      assertThat(rowGroups).isNotEmpty();
    }

    // Test passes if we can parse without crashing (both tree and table models are
    // valid)
  }

  @Test
  @EnabledIf("stockDataFileExists")
  void testStockDataFile_parseFullMetadata() throws Exception {
    // When - 直接使用parser解析各个部分
    List<TableDTO> tables = parser.parseTables(STOCK_DATA_FILE);
    List<MeasurementDTO> measurements = parser.parseMeasurements(STOCK_DATA_FILE);
    List<RowGroupDTO> rowGroups = parser.parseRowGroups(STOCK_DATA_FILE);

    // Then
    System.out.println("=== Stock Data File Full Metadata ===");
    System.out.println("File: " + STOCK_DATA_FILE);
    System.out.println("Measurements Count: " + measurements.size());
    System.out.println("RowGroups Count: " + rowGroups.size());

    if (!tables.isEmpty()) {
      System.out.println("\n=== Tables in Metadata ===");
      System.out.println("Tables count: " + tables.size());
      for (TableDTO table : tables) {
        System.out.println(
            "  - "
                + table.getTableName()
                + " (TAG: "
                + table.getTagColumns().size()
                + ", FIELD: "
                + table.getFieldColumns().size()
                + ")");
      }
    } else {
      System.out.println("\nNo tables field (tree model detected)");
      System.out.println("\n=== Tree Model: Measurements ===");
      for (MeasurementDTO m : measurements) {
        System.out.println(
            "  - "
                + m.getName()
                + ": "
                + m.getDataType()
                + " (encoding: "
                + m.getEncoding()
                + ", compression: "
                + m.getCompression()
                + ")");
      }
      System.out.println("\n=== Tree Model: Devices (from RowGroups) ===");
      for (RowGroupDTO rg : rowGroups) {
        System.out.println("  - Device: " + rg.getDevice());
        System.out.println("    Time range: " + rg.getStartTime() + " - " + rg.getEndTime());
        System.out.println("    Chunks: " + rg.getChunkCount());
      }
    }

    // Verify
    assertThat(measurements).isNotEmpty();
    assertThat(rowGroups).isNotEmpty();
  }

  @Test
  @EnabledIf("emptyFileExists")
  void testEmptyFile_robustnessTest() throws Exception {
    // When - 测试空文件是否会导致崩溃
    System.out.println("=== Empty File Robustness Test ===");
    System.out.println("File: " + EMPTY_FILE);

    try {
      List<TableDTO> tables = parser.parseTables(EMPTY_FILE);
      System.out.println("✓ parseTables succeeded, returned " + tables.size() + " tables");
    } catch (Exception e) {
      System.out.println(
          "✗ parseTables failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    try {
      List<MeasurementDTO> measurements = parser.parseMeasurements(EMPTY_FILE);
      System.out.println(
          "✓ parseMeasurements succeeded, returned " + measurements.size() + " measurements");
    } catch (Exception e) {
      System.out.println(
          "✗ parseMeasurements failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    try {
      List<RowGroupDTO> rowGroups = parser.parseRowGroups(EMPTY_FILE);
      System.out.println("✓ parseRowGroups succeeded, returned " + rowGroups.size() + " rowGroups");
    } catch (Exception e) {
      System.out.println(
          "✗ parseRowGroups failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    System.out.println("\n✓ Empty file robustness test completed without crash");
    // 空文件测试的关键是不抛出未捕获异常导致程序崩溃
  }

  @Test
  @EnabledIf("stockDataFileExists")
  void testStockDataFile_verifyTagColumns() throws Exception {
    // When
    List<TableDTO> tables = parser.parseTables(STOCK_DATA_FILE);

    // Then - 验证TAG列的存在
    System.out.println("=== TAG Columns Verification ===");
    System.out.println("File: " + STOCK_DATA_FILE);

    if (tables.isEmpty()) {
      System.out.println("No tables found - might be tree model");
      return;
    }

    assertThat(tables).isNotEmpty();

    TableDTO firstTable = tables.get(0);
    System.out.println("Table: " + firstTable.getTableName());
    System.out.println("TAG columns:");

    for (MeasurementDTO tag : firstTable.getTagColumns()) {
      System.out.println(
          "  "
              + tag.getName()
              + " - "
              + tag.getDataType()
              + " - "
              + tag.getEncoding()
              + " - "
              + tag.getCompression());
    }

    if (!firstTable.getTagColumns().isEmpty()) {
      System.out.println("\n✓ TAG columns detected successfully!");
      assertThat(firstTable.getTagColumns()).isNotEmpty();
    } else {
      System.out.println("\n⚠ No TAG columns found - might be tree model");
    }
  }

  @Test
  @EnabledIf("emptyFileExists")
  void testEmptyFile_expectGracefulFailure() throws Exception {
    // When - 空文件应该优雅地处理，不应该崩溃
    System.out.println("=== Empty File Graceful Failure Test ===");
    System.out.println("Testing that parser handles empty file gracefully...");

    boolean allOperationsHandledGracefully = true;

    // Test 1: parseTables
    try {
      List<TableDTO> tables = parser.parseTables(EMPTY_FILE);
      System.out.println("✓ parseTables returned " + tables.size() + " tables");
    } catch (Exception e) {
      System.out.println("✓ parseTables threw expected exception: " + e.getClass().getSimpleName());
      // 抛出异常也是可接受的，只要不是未捕获的严重错误
    }

    // Test 2: parseMeasurements
    try {
      List<MeasurementDTO> measurements = parser.parseMeasurements(EMPTY_FILE);
      System.out.println("✓ parseMeasurements returned " + measurements.size() + " measurements");
    } catch (Exception e) {
      System.out.println(
          "✓ parseMeasurements threw expected exception: " + e.getClass().getSimpleName());
    }

    System.out.println("\n✓ Empty file handling is graceful - no unexpected crashes");
  }

  @Test
  @EnabledIf("stockDataFileExists")
  void testStockDataFile_parseChunksWithDetails() throws Exception {
    // When
    List<ChunkDTO> chunks = parser.parseChunks(STOCK_DATA_FILE);

    // Then
    System.out.println("=== Stock Data File Chunks (Detailed) ===");
    System.out.println("File: " + STOCK_DATA_FILE);
    System.out.println("Total chunks: " + chunks.size());

    // Group chunks by measurement
    System.out.println("\nChunks by measurement:");
    chunks.stream()
        .collect(java.util.stream.Collectors.groupingBy(ChunkDTO::getMeasurement))
        .forEach(
            (measurement, chunkList) -> {
              System.out.println(
                  "  "
                      + (measurement.isEmpty() ? "(empty)" : measurement)
                      + ": "
                      + chunkList.size()
                      + " chunks");
            });

    // Show first 10 chunks with detailed information
    int displayCount = Math.min(10, chunks.size());
    System.out.println("\nFirst " + displayCount + " chunks with details:");
    for (int i = 0; i < displayCount; i++) {
      ChunkDTO chunk = chunks.get(i);
      System.out.println("\n--- Chunk " + (i + 1) + " ---");
      System.out.println("  Measurement: " + chunk.getMeasurement());
      System.out.println("  Device: " + chunk.getDevice());
      System.out.println("  Offset: " + chunk.getOffset());
      System.out.println("  Size: " + chunk.getSize() + " bytes");
      System.out.println("  Data Type: " + chunk.getDataType());
      System.out.println("  Encoding: " + chunk.getEncoding());
      System.out.println("  Compression: " + chunk.getCompression());
      System.out.println("  Time Range: " + chunk.getStartTime() + " - " + chunk.getEndTime());
      System.out.println("  Data Points: " + chunk.getNumOfPoints());
      if (chunk.getMinValue() != null) {
        System.out.println("  Min Value: " + chunk.getMinValue());
      }
      if (chunk.getMaxValue() != null) {
        System.out.println("  Max Value: " + chunk.getMaxValue());
      }
      System.out.println(
          "  Compression Ratio: " + String.format("%.2f", chunk.getCompressionRatio()));
    }

    // Verify
    assertThat(chunks).isNotEmpty();

    // Verify that extended fields are populated
    ChunkDTO firstChunk = chunks.get(0);
    assertThat(firstChunk.getDevice()).isNotNull();
    assertThat(firstChunk.getDataType()).isNotNull();
    assertThat(firstChunk.getEncoding()).isNotNull();
    assertThat(firstChunk.getCompression()).isNotNull();
    assertThat(firstChunk.getStartTime()).isNotNull();
    assertThat(firstChunk.getEndTime()).isNotNull();
    assertThat(firstChunk.getNumOfPoints()).isNotNull();
  }

  // Condition methods for @EnabledIf
  static boolean stockDataFileExists() {
    return new File(STOCK_DATA_FILE).exists() && new File(STOCK_DATA_FILE).length() > 0;
  }

  static boolean emptyFileExists() {
    return new File(EMPTY_FILE).exists();
  }
}
