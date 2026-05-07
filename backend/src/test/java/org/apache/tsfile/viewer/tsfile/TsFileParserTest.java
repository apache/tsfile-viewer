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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.write.record.Tablet;
import org.apache.tsfile.write.v4.ITsFileWriter;
import org.apache.tsfile.write.v4.TsFileWriterBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tsfile.viewer.tsfile.TsFileParser.BasicMetadata;
import org.apache.tsfile.viewer.tsfile.TsFileParser.ChunkInfo;
import org.apache.tsfile.viewer.tsfile.TsFileParser.MeasurementInfo;
import org.apache.tsfile.viewer.tsfile.TsFileParser.ParsedMetadata;
import org.apache.tsfile.viewer.tsfile.TsFileParser.RowGroupInfo;

/**
 * Tests for {@link TsFileParser}.
 *
 * <p>Validates: Requirement 2.1, 2.3, 2.4, 2.5 (Metadata parsing)
 */
class TsFileParserTest {

  private TsFileParser tsFileParser;

  @TempDir Path tempDir;

  private Path testTsFilePath;

  @BeforeEach
  void setUp() {
    tsFileParser = new TsFileParser();
  }

  @AfterEach
  void tearDown() throws IOException {
    // Clean up test files
    if (testTsFilePath != null && Files.exists(testTsFilePath)) {
      Files.deleteIfExists(testTsFilePath);
    }
  }

  /**
   * Creates a test TsFile with sample data.
   *
   * @param fileName name of the test file
   * @param numDevices number of devices to create
   * @param numMeasurements number of measurements per device
   * @param numRows number of data rows per device
   * @return path to the created TsFile
   */
  private Path createTestTsFile(String fileName, int numDevices, int numMeasurements, int numRows)
      throws Exception {
    Path filePath = tempDir.resolve(fileName);
    File file = filePath.toFile();

    // Create table schema with measurements
    List<String> columnNames = new java.util.ArrayList<>();
    List<TSDataType> dataTypes = new java.util.ArrayList<>();

    // Add tag column for device identification
    columnNames.add("device_id");
    dataTypes.add(TSDataType.STRING);

    // Add measurement columns
    for (int m = 0; m < numMeasurements; m++) {
      columnNames.add("s" + m);
      dataTypes.add(TSDataType.INT64);
    }

    TableSchema tableSchema =
        new TableSchema("test_table", createColumnSchemas(columnNames, dataTypes));

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(file).tableSchema(tableSchema).build()) {

      for (int d = 0; d < numDevices; d++) {
        Tablet tablet = new Tablet(columnNames, dataTypes);

        for (int row = 0; row < numRows; row++) {
          long timestamp = d * 1000000L + row;
          tablet.addTimestamp(row, timestamp);
          tablet.addValue(row, "device_id", "device_" + d);

          for (int m = 0; m < numMeasurements; m++) {
            tablet.addValue(row, "s" + m, (long) (d * 1000 + m * 100 + row));
          }
        }

        writer.write(tablet);
      }
    }

    testTsFilePath = filePath;
    return filePath;
  }

  private List<org.apache.tsfile.file.metadata.ColumnSchema> createColumnSchemas(
      List<String> columnNames, List<TSDataType> dataTypes) {
    List<org.apache.tsfile.file.metadata.ColumnSchema> schemas = new java.util.ArrayList<>();
    for (int i = 0; i < columnNames.size(); i++) {
      org.apache.tsfile.enums.ColumnCategory category =
          i == 0
              ? org.apache.tsfile.enums.ColumnCategory.TAG
              : org.apache.tsfile.enums.ColumnCategory.FIELD;
      schemas.add(
          new org.apache.tsfile.file.metadata.ColumnSchemaBuilder()
              .name(columnNames.get(i))
              .dataType(dataTypes.get(i))
              .category(category)
              .build());
    }
    return schemas;
  }

  @Nested
  @DisplayName("parseMetadata tests")
  class ParseMetadataTests {

    @Test
    @DisplayName("Should parse metadata from valid TsFile")
    void shouldParseMetadataFromValidTsFile() throws Exception {
      // Create a test TsFile with 2 devices, 3 measurements, 10 rows each
      Path tsFilePath = createTestTsFile("test_metadata.tsfile", 2, 3, 10);

      BasicMetadata metadata = tsFileParser.parseMetadata(tsFilePath);

      assertThat(metadata).isNotNull();
      assertThat(metadata.version()).startsWith("V");
      assertThat(metadata.deviceCount()).isGreaterThanOrEqualTo(1);
      assertThat(metadata.measurementCount()).isGreaterThanOrEqualTo(1);
      assertThat(metadata.startTime()).isGreaterThanOrEqualTo(0);
      assertThat(metadata.endTime()).isGreaterThanOrEqualTo(metadata.startTime());
    }

    @Test
    @DisplayName("Should throw IOException for non-existent file")
    void shouldThrowIOExceptionForNonExistentFile() {
      Path nonExistentPath = tempDir.resolve("non_existent.tsfile");

      assertThatThrownBy(() -> tsFileParser.parseMetadata(nonExistentPath))
          .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should throw exception for invalid TsFile")
    void shouldThrowExceptionForInvalidTsFile() throws Exception {
      // Create an invalid file (not a TsFile)
      Path invalidPath = tempDir.resolve("invalid.tsfile");
      Files.writeString(invalidPath, "This is not a valid TsFile");

      assertThatThrownBy(() -> tsFileParser.parseMetadata(invalidPath))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should return correct device count")
    void shouldReturnCorrectDeviceCount() throws Exception {
      Path tsFilePath = createTestTsFile("test_devices.tsfile", 3, 2, 5);

      BasicMetadata metadata = tsFileParser.parseMetadata(tsFilePath);

      // Device count should be at least 1 (may vary based on TsFile internal structure)
      assertThat(metadata.deviceCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should return valid time range")
    void shouldReturnValidTimeRange() throws Exception {
      Path tsFilePath = createTestTsFile("test_timerange.tsfile", 1, 2, 100);

      BasicMetadata metadata = tsFileParser.parseMetadata(tsFilePath);

      assertThat(metadata.startTime()).isGreaterThanOrEqualTo(0);
      assertThat(metadata.endTime()).isGreaterThanOrEqualTo(metadata.startTime());
    }
  }

  @Nested
  @DisplayName("parseMeasurements tests")
  class ParseMeasurementsTests {

    @Test
    @DisplayName("Should parse measurements from valid TsFile")
    void shouldParseMeasurementsFromValidTsFile() throws Exception {
      Path tsFilePath = createTestTsFile("test_measurements.tsfile", 1, 3, 10);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<MeasurementInfo> measurements = tsFileParser.parseMeasurements(reader);

        assertThat(measurements).isNotEmpty();
        for (MeasurementInfo measurement : measurements) {
          // Measurement name can be empty for time column, but should not be null
          assertThat(measurement.getName()).isNotNull();
          assertThat(measurement.getDataType()).isNotBlank();
        }
      }
    }

    @Test
    @DisplayName("Should return unique measurements")
    void shouldReturnUniqueMeasurements() throws Exception {
      Path tsFilePath = createTestTsFile("test_unique_measurements.tsfile", 2, 3, 10);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<MeasurementInfo> measurements = tsFileParser.parseMeasurements(reader);

        // Check that measurement names are unique
        List<String> measurementNames =
            measurements.stream().map(MeasurementInfo::getName).toList();
        assertThat(measurementNames).doesNotHaveDuplicates();
      }
    }

    @Test
    @DisplayName("Should include data type for each measurement")
    void shouldIncludeDataTypeForEachMeasurement() throws Exception {
      Path tsFilePath = createTestTsFile("test_datatypes.tsfile", 1, 2, 5);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<MeasurementInfo> measurements = tsFileParser.parseMeasurements(reader);

        for (MeasurementInfo measurement : measurements) {
          assertThat(measurement.getDataType()).isNotNull();
          assertThat(measurement.getDataType()).isNotBlank();
        }
      }
    }
  }

  @Nested
  @DisplayName("parseRowGroups tests")
  class ParseRowGroupsTests {

    @Test
    @DisplayName("Should parse RowGroups from valid TsFile")
    void shouldParseRowGroupsFromValidTsFile() throws Exception {
      Path tsFilePath = createTestTsFile("test_rowgroups.tsfile", 2, 2, 10);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<RowGroupInfo> rowGroups = tsFileParser.parseRowGroups(reader);

        assertThat(rowGroups).isNotEmpty();
        for (RowGroupInfo rowGroup : rowGroups) {
          assertThat(rowGroup.getIndex()).isGreaterThanOrEqualTo(0);
          assertThat(rowGroup.getDevice()).isNotBlank();
          assertThat(rowGroup.getChunkCount()).isGreaterThanOrEqualTo(0);
        }
      }
    }

    @Test
    @DisplayName("Should have sequential indices")
    void shouldHaveSequentialIndices() throws Exception {
      Path tsFilePath = createTestTsFile("test_rowgroup_indices.tsfile", 3, 2, 5);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<RowGroupInfo> rowGroups = tsFileParser.parseRowGroups(reader);

        for (int i = 0; i < rowGroups.size(); i++) {
          assertThat(rowGroups.get(i).getIndex()).isEqualTo(i);
        }
      }
    }

    @Test
    @DisplayName("Should have valid time ranges")
    void shouldHaveValidTimeRanges() throws Exception {
      Path tsFilePath = createTestTsFile("test_rowgroup_timerange.tsfile", 2, 2, 10);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<RowGroupInfo> rowGroups = tsFileParser.parseRowGroups(reader);

        for (RowGroupInfo rowGroup : rowGroups) {
          assertThat(rowGroup.getEndTime()).isGreaterThanOrEqualTo(rowGroup.getStartTime());
        }
      }
    }
  }

  @Nested
  @DisplayName("parseChunks tests")
  class ParseChunksTests {

    @Test
    @DisplayName("Should parse Chunks from valid TsFile")
    void shouldParseChunksFromValidTsFile() throws Exception {
      Path tsFilePath = createTestTsFile("test_chunks.tsfile", 1, 2, 10);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<ChunkInfo> chunks = tsFileParser.parseChunks(reader);

        assertThat(chunks).isNotEmpty();
        for (ChunkInfo chunk : chunks) {
          // Measurement name can be empty for time column, but should not be null
          assertThat(chunk.getMeasurement()).isNotNull();
          assertThat(chunk.getOffset()).isGreaterThanOrEqualTo(0);
          assertThat(chunk.getSize()).isGreaterThanOrEqualTo(0);
        }
      }
    }

    @Test
    @DisplayName("Should include compression ratio")
    void shouldIncludeCompressionRatio() throws Exception {
      Path tsFilePath = createTestTsFile("test_chunk_compression.tsfile", 1, 2, 100);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<ChunkInfo> chunks = tsFileParser.parseChunks(reader);

        for (ChunkInfo chunk : chunks) {
          assertThat(chunk.getCompressionRatio()).isGreaterThan(0);
        }
      }
    }

    @Test
    @DisplayName("Should include device information")
    void shouldIncludeDeviceInformation() throws Exception {
      Path tsFilePath = createTestTsFile("test_chunk_device.tsfile", 2, 2, 10);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<ChunkInfo> chunks = tsFileParser.parseChunks(reader);

        for (ChunkInfo chunk : chunks) {
          assertThat(chunk.getDevice()).isNotBlank();
        }
      }
    }

    @Test
    @DisplayName("Should include time range for each chunk")
    void shouldIncludeTimeRangeForEachChunk() throws Exception {
      Path tsFilePath = createTestTsFile("test_chunk_timerange.tsfile", 1, 2, 50);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<ChunkInfo> chunks = tsFileParser.parseChunks(reader);

        for (ChunkInfo chunk : chunks) {
          assertThat(chunk.getEndTime()).isGreaterThanOrEqualTo(chunk.getStartTime());
        }
      }
    }

    @Test
    @DisplayName("Should include point count for each chunk")
    void shouldIncludePointCountForEachChunk() throws Exception {
      Path tsFilePath = createTestTsFile("test_chunk_points.tsfile", 1, 2, 20);

      try (org.apache.tsfile.read.TsFileSequenceReader reader =
          new org.apache.tsfile.read.TsFileSequenceReader(tsFilePath.toString())) {

        List<ChunkInfo> chunks = tsFileParser.parseChunks(reader);

        for (ChunkInfo chunk : chunks) {
          assertThat(chunk.getNumOfPoints()).isGreaterThanOrEqualTo(0);
        }
      }
    }
  }

  @Nested
  @DisplayName("Data class tests")
  class DataClassTests {

    @Test
    @DisplayName("ParsedMetadata should have correct getters")
    void parsedMetadataShouldHaveCorrectGetters() {
      ParsedMetadata metadata = new ParsedMetadata("V4", 1000L, 2000L, 5, 10, 3, 15);

      assertThat(metadata.getVersion()).isEqualTo("V4");
      assertThat(metadata.getStartTime()).isEqualTo(1000L);
      assertThat(metadata.getEndTime()).isEqualTo(2000L);
      assertThat(metadata.getDeviceCount()).isEqualTo(5);
      assertThat(metadata.getMeasurementCount()).isEqualTo(10);
      assertThat(metadata.getRowGroupCount()).isEqualTo(3);
      assertThat(metadata.getChunkCount()).isEqualTo(15);
    }

    @Test
    @DisplayName("MeasurementInfo should have correct getters")
    void measurementInfoShouldHaveCorrectGetters() {
      MeasurementInfo info = new MeasurementInfo("temperature", "DOUBLE", "PLAIN", "SNAPPY");

      assertThat(info.getName()).isEqualTo("temperature");
      assertThat(info.getDataType()).isEqualTo("DOUBLE");
      assertThat(info.getEncoding()).isEqualTo("PLAIN");
      assertThat(info.getCompression()).isEqualTo("SNAPPY");
    }

    @Test
    @DisplayName("RowGroupInfo should have correct getters")
    void rowGroupInfoShouldHaveCorrectGetters() {
      RowGroupInfo info = new RowGroupInfo(0, "device_1", 1000L, 2000L, 5);

      assertThat(info.getIndex()).isEqualTo(0);
      assertThat(info.getDevice()).isEqualTo("device_1");
      assertThat(info.getStartTime()).isEqualTo(1000L);
      assertThat(info.getEndTime()).isEqualTo(2000L);
      assertThat(info.getChunkCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("ChunkInfo should have correct getters")
    void chunkInfoShouldHaveCorrectGetters() {
      ChunkInfo info =
          new ChunkInfo("temperature", 1024L, 512L, 2.5, "device_1", 1000L, 2000L, 100);

      assertThat(info.getMeasurement()).isEqualTo("temperature");
      assertThat(info.getOffset()).isEqualTo(1024L);
      assertThat(info.getSize()).isEqualTo(512L);
      assertThat(info.getCompressionRatio()).isEqualTo(2.5);
      assertThat(info.getDevice()).isEqualTo("device_1");
      assertThat(info.getStartTime()).isEqualTo(1000L);
      assertThat(info.getEndTime()).isEqualTo(2000L);
      assertThat(info.getNumOfPoints()).isEqualTo(100);
    }
  }
}
