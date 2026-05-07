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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tsfile.viewer.dto.MeasurementDTO;

/**
 * Test for TAG column identification in V4 table model TsFile.
 *
 * <p>This test uses a real TsFile from the wind dataset to verify that TAG columns are correctly
 * identified and parsed.
 */
public class TsFileParserTagColumnTest {

  private static final Logger logger = LoggerFactory.getLogger(TsFileParserTagColumnTest.class);

  // Real file path from user's system
  private static final String TEST_FILE_PATH =
      "/Users/critas/tsfile-samples/tsfiles/wind/159/2671/882971982187-2-1-0.tsfile";

  private TsFileParser parser;
  private boolean fileExists;

  @BeforeEach
  void setUp() {
    parser = new TsFileParser();
    Path path = Paths.get(TEST_FILE_PATH);
    fileExists = Files.exists(path);

    if (!fileExists) {
      logger.warn("Test file does not exist: {}", TEST_FILE_PATH);
      logger.warn("This test will be skipped");
    } else {
      logger.info("Test file found: {}", TEST_FILE_PATH);
      try {
        long fileSize = Files.size(path);
        logger.info("File size: {} bytes", fileSize);
      } catch (IOException e) {
        logger.error("Failed to get file size", e);
      }
    }
  }

  @Test
  void testParseMeasurementsWithTagColumns() throws IOException {
    // Skip test if file doesn't exist
    org.junit.jupiter.api.Assumptions.assumeTrue(fileExists, "Test file not found, skipping test");

    // Parse measurements from the TsFile
    List<MeasurementDTO> measurements = parser.parseMeasurements(TEST_FILE_PATH);

    // Log all measurements with their categories
    logger.info("Found {} measurements:", measurements.size());
    for (MeasurementDTO m : measurements) {
      logger.info(
          "  - {} (type: {}, encoding: {}, compression: {}, category: {})",
          m.getName(),
          m.getDataType(),
          m.getEncoding(),
          m.getCompression(),
          m.getColumnCategory());
    }

    // Basic assertions
    assertNotNull(measurements, "Measurements should not be null");
    assertFalse(measurements.isEmpty(), "Should have at least one measurement");

    // Count TAG and FIELD columns
    long tagCount = measurements.stream().filter(m -> "TAG".equals(m.getColumnCategory())).count();
    long fieldCount =
        measurements.stream().filter(m -> "FIELD".equals(m.getColumnCategory())).count();

    logger.info("TAG columns: {}", tagCount);
    logger.info("FIELD columns: {}", fieldCount);

    // Assertions for TAG columns
    assertTrue(tagCount > 0, "Should have at least one TAG column");
    assertTrue(fieldCount > 0, "Should have at least one FIELD column");

    // Find and log TAG columns specifically
    List<MeasurementDTO> tagColumns =
        measurements.stream().filter(m -> "TAG".equals(m.getColumnCategory())).toList();

    logger.info("TAG columns details:");
    for (MeasurementDTO tag : tagColumns) {
      logger.info(
          "  TAG: {} (type: {}, encoding: {}, compression: {})",
          tag.getName(),
          tag.getDataType(),
          tag.getEncoding(),
          tag.getCompression());
    }

    // Find and log FIELD columns
    List<MeasurementDTO> fieldColumns =
        measurements.stream().filter(m -> "FIELD".equals(m.getColumnCategory())).toList();

    logger.info("FIELD columns details:");
    for (MeasurementDTO field : fieldColumns) {
      logger.info(
          "  FIELD: {} (type: {}, encoding: {}, compression: {})",
          field.getName(),
          field.getDataType(),
          field.getEncoding(),
          field.getCompression());
    }

    // Verify that columnCategory is not null for any measurement
    for (MeasurementDTO m : measurements) {
      assertNotNull(
          m.getColumnCategory(),
          "Column category should not be null for measurement: " + m.getName());
      assertTrue(
          m.getColumnCategory().equals("TAG") || m.getColumnCategory().equals("FIELD"),
          "Column category should be TAG or FIELD, got: " + m.getColumnCategory());
    }
  }

  @Test
  void testParseMetadataWithTagColumns() throws IOException {
    // Skip test if file doesn't exist
    org.junit.jupiter.api.Assumptions.assumeTrue(fileExists, "Test file not found, skipping test");

    // Parse full metadata
    TsFileParser.BasicMetadata metadata = parser.parseMetadata(TEST_FILE_PATH);

    logger.info("Metadata parsed successfully:");
    logger.info("  Version: {}", metadata.version());
    logger.info("  Time range: {} - {}", metadata.startTime(), metadata.endTime());
    logger.info("  Device count: {}", metadata.deviceCount());
    logger.info("  Measurement count: {}", metadata.measurementCount());
    logger.info("  RowGroup count: {}", metadata.rowGroupCount());
    logger.info("  Chunk count: {}", metadata.chunkCount());

    assertNotNull(metadata, "Metadata should not be null");
    assertTrue(metadata.measurementCount() > 0, "Should have at least one measurement");

    // For V4 table model files, measurement count should include both TAG and FIELD
    // columns
    // Expected: 2 TAG columns (wind_plant, turbine) + 9 FIELD columns = 11 total
    logger.info("Note: Measurement count includes both TAG and FIELD columns in V4 table model");
  }
}
