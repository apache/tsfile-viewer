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

package org.apache.tsfile.viewer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.file.metadata.ColumnSchemaBuilder;
import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.dto.DataPreviewRequest;
import org.apache.tsfile.viewer.dto.ExportFormat;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.tsfile.TsFileDataReader;
import org.apache.tsfile.viewer.tsfile.TsFileTestUtils;
import org.apache.tsfile.write.record.Tablet;
import org.apache.tsfile.write.v4.ITsFileWriter;
import org.apache.tsfile.write.v4.TsFileWriterBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for the streaming data export.
 *
 * <p>Covers the CSV layout (column order, ISO timestamps, RFC 4180 escaping), the JSON structure,
 * the reuse of the preview filters, and the fact that an export is not truncated at {@code
 * tsfile.query.max-result-size}.
 */
class DataServiceExportTest {

  private static final String TABLE_NAME = "fault_table";
  private static final long START_TIME_NS = 1_719_731_133_000_000_000L;
  private static final long INTERVAL_NS = 1_000_000_000L;
  private static final int POINT_COUNT = 5;

  /** A TAG value carrying a comma and a quote, so the CSV escaping is exercised end to end. */
  private static final String DEVICE_ID = "C919,\"A\"";

  private static final String ESCAPED_DEVICE = "\"fault_table.C919,\"\"A\"\"\"";

  /** Well above the default {@code tsfile.query.max-result-size} of 10000. */
  private static final int LARGE_ROW_COUNT = 10_500;

  /** A {@link Tablet} holds at most ~1024 rows, larger files have to be written in batches. */
  private static final int TABLET_BATCH_SIZE = 1_000;

  private Path tempDir;
  private TsFileProperties properties;
  private FileService fileService;
  private DataService dataService;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    tempDir = Files.createTempDirectory("data-service-export-test");

    properties = new TsFileProperties();
    properties.getAllowedDirectories().add(tempDir.toString());
    properties.getQuery().setTimeoutSeconds(30);

    fileService = new FileService(properties, new PathValidationService(properties));
    objectMapper = new ObjectMapper();
    dataService = new DataService(properties, fileService, new TsFileDataReader());
  }

  @AfterEach
  void tearDown() throws Exception {
    if (tempDir == null || !Files.exists(tempDir)) {
      return;
    }
    Files.walk(tempDir)
        .sorted((a, b) -> -a.compareTo(b))
        .forEach(
            path -> {
              try {
                Files.deleteIfExists(path);
              } catch (Exception e) {
                // Ignore cleanup errors
              }
            });
  }

  @Test
  @DisplayName("CSV starts with Timestamp,Device followed by the sorted measurement columns")
  void shouldWriteCsvHeaderWithSortedColumns() throws Exception {
    String csv = exportCsv(smallFileId());

    String[] lines = csv.split("\n");
    assertThat(lines[0]).isEqualTo("Timestamp,Device,device_id,value");
    assertThat(lines).hasSize(POINT_COUNT + 1);
  }

  @Test
  @DisplayName("CSV writes ISO timestamps in UTC and escapes separators and quotes")
  void shouldWriteIsoTimestampsAndEscapedFields() throws Exception {
    String csv = exportCsv(smallFileId());

    String[] lines = csv.split("\n");
    assertThat(lines[1])
        .isEqualTo("2024-06-30T07:05:33.000Z," + ESCAPED_DEVICE + ",\"C919,\"\"A\"\"\",false");
    assertThat(lines[2]).startsWith("2024-06-30T07:05:34.000Z,");
  }

  @Test
  @DisplayName("CSV quoting follows RFC 4180 for line breaks and quotes")
  void shouldQuoteFieldsContainingLineBreaks() {
    assertThat(DataService.escapeCsv("plain")).isEqualTo("plain");
    assertThat(DataService.escapeCsv(null)).isEmpty();
    assertThat(DataService.escapeCsv("a,b")).isEqualTo("\"a,b\"");
    assertThat(DataService.escapeCsv("a\"b")).isEqualTo("\"a\"\"b\"");
    assertThat(DataService.escapeCsv("a\nb")).isEqualTo("\"a\nb\"");
    assertThat(DataService.escapeCsv("a\r\nb")).isEqualTo("\"a\r\nb\"");
  }

  @Test
  @DisplayName("Timestamps of every supported precision normalize to the same ISO instant")
  void shouldNormalizeTimestampPrecision() {
    long millis = 1_719_731_133_000L;
    assertThat(DataService.formatTimestamp(millis)).isEqualTo("2024-06-30T07:05:33.000Z");
    assertThat(DataService.formatTimestamp(millis * 1_000L)).isEqualTo("2024-06-30T07:05:33.000Z");
    assertThat(DataService.formatTimestamp(millis * 1_000_000L))
        .isEqualTo("2024-06-30T07:05:33.000Z");
    assertThat(DataService.formatTimestamp(millis / 1_000L)).isEqualTo("2024-06-30T07:05:33.000Z");
  }

  @Test
  @DisplayName("JSON is an array of objects shaped like DataPreviewResponse.data rows")
  void shouldWriteJsonArrayOfRowObjects() throws Exception {
    String json = exportToString(request(smallFileId()), ExportFormat.JSON);

    List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<>() {});
    assertThat(rows).hasSize(POINT_COUNT);

    Map<String, Object> first = rows.get(0);
    assertThat(first).containsOnlyKeys("timestamp", "device", "measurements");
    assertThat(first.get("timestamp")).isEqualTo(1_719_731_133_000_000_000L);
    assertThat(first.get("device")).isEqualTo("fault_table." + DEVICE_ID);

    @SuppressWarnings("unchecked")
    Map<String, Object> measurements = (Map<String, Object>) first.get("measurements");
    assertThat(measurements).containsOnlyKeys("device_id", "value");
    assertThat(measurements.get("device_id")).isEqualTo(DEVICE_ID);
    assertThat(measurements.get("value")).isEqualTo(false);
  }

  @Test
  @DisplayName("An empty filter selection exports every row, not just one page")
  void shouldIgnoreLimitAndOffset() throws Exception {
    DataPreviewRequest request = request(smallFileId());
    request.setLimit(1);
    request.setOffset(3);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    long rows = dataService.exportData(dataService.prepareExport(request, ExportFormat.CSV), out);

    assertThat(rows).isEqualTo(POINT_COUNT);
    // Header + 5 data rows: the request's own limit/offset never reach the export.
    assertThat(out.toString(StandardCharsets.UTF_8).split("\n")).hasSize(POINT_COUNT + 1);
  }

  @Test
  @DisplayName("The preview filters are reused by the export")
  void shouldApplyPreviewFilters() throws Exception {
    DataPreviewRequest matchingDevice = request(smallFileId());
    matchingDevice.setDevices(List.of("fault_table." + DEVICE_ID));
    assertThat(exportCsv(matchingDevice).split("\n")).hasSize(POINT_COUNT + 1);

    DataPreviewRequest unknownDevice = request(smallFileId());
    unknownDevice.setDevices(List.of("fault_table.unknown"));
    assertThat(exportCsv(unknownDevice).split("\n")).hasSize(1);
  }

  @Test
  @DisplayName("A measurement filter keeps only the selected CSV columns")
  void shouldTrimCsvColumnsToSelectedMeasurements() throws Exception {
    DataPreviewRequest request = request(smallFileId());
    request.setMeasurements(List.of("value"));

    String[] lines = exportCsv(request).split("\n");
    assertThat(lines[0]).isEqualTo("Timestamp,Device,value");
    assertThat(lines).hasSize(POINT_COUNT + 1);
  }

  @Test
  @DisplayName("Exporting more than max-result-size rows is not truncated")
  void shouldExportMoreRowsThanMaxResultSize() throws Exception {
    File largeFile = createLargeTableModelFile(tempDir.resolve("large.tsfile"), LARGE_ROW_COUNT);

    assertThat(LARGE_ROW_COUNT).isGreaterThan(properties.getQuery().getMaxResultSize());

    String csv = exportCsv(fileService.registerFile(largeFile.getAbsolutePath()));

    String[] lines = csv.split("\n");
    assertThat(lines[0]).isEqualTo("Timestamp,Device,device_id,humidity,temperature");
    assertThat(lines).hasSize(LARGE_ROW_COUNT + 1);
  }

  @Test
  @DisplayName("An unknown fileId keeps the preview semantics (404)")
  void shouldThrowWhenFileIsUnknown() {
    String missingPath = tempDir.resolve("missing.tsfile").toAbsolutePath().toString();
    String fileId =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(missingPath.getBytes(StandardCharsets.UTF_8));
    DataPreviewRequest request = request(fileId);

    assertThatThrownBy(() -> dataService.prepareExport(request, ExportFormat.CSV))
        .isInstanceOf(TsFileNotFoundException.class);
  }

  @Test
  @DisplayName("format parameter parsing accepts csv/json in any case and rejects the rest")
  void shouldParseFormatParameter() {
    assertThat(ExportFormat.from("csv")).isEqualTo(ExportFormat.CSV);
    assertThat(ExportFormat.from("JSON")).isEqualTo(ExportFormat.JSON);
    assertThat(ExportFormat.from(null)).isEqualTo(ExportFormat.CSV);
    assertThatThrownBy(() -> ExportFormat.from("xlsx"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * Creates a table model file with {@code rowCount} rows.
   *
   * <p>A single {@link Tablet} holds at most ~1024 rows, so the rows are written in batches; this
   * is what makes a file larger than {@code tsfile.query.max-result-size} available to the test.
   */
  private File createLargeTableModelFile(Path outputPath, int rowCount) throws Exception {
    Files.deleteIfExists(outputPath);
    File file = outputPath.toFile();

    TableSchema tableSchema =
        new TableSchema(
            "large_table",
            Arrays.asList(
                new ColumnSchemaBuilder()
                    .name("device_id")
                    .dataType(TSDataType.STRING)
                    .category(ColumnCategory.TAG)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("humidity")
                    .dataType(TSDataType.INT32)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("temperature")
                    .dataType(TSDataType.FLOAT)
                    .category(ColumnCategory.FIELD)
                    .build()));

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(file).tableSchema(tableSchema).build()) {
      int written = 0;
      while (written < rowCount) {
        int batchSize = Math.min(TABLET_BATCH_SIZE, rowCount - written);
        Tablet tablet =
            new Tablet(
                Arrays.asList("device_id", "humidity", "temperature"),
                Arrays.asList(TSDataType.STRING, TSDataType.INT32, TSDataType.FLOAT));
        for (int i = 0; i < batchSize; i++) {
          int row = written + i;
          tablet.addTimestamp(i, row * 1_000L);
          tablet.addValue(i, "device_id", "C919");
          tablet.addValue(i, "humidity", 50 + row);
          tablet.addValue(i, "temperature", 20.0f + row * 0.5f);
        }
        writer.write(tablet);
        written += batchSize;
      }
    }

    return file;
  }

  private String smallFileId() throws Exception {
    File file =
        TsFileTestUtils.createTableModelBooleanField(
            tempDir.resolve("small.tsfile"),
            TABLE_NAME,
            DEVICE_ID,
            START_TIME_NS,
            INTERVAL_NS,
            POINT_COUNT);
    return fileService.registerFile(file.getAbsolutePath());
  }

  private DataPreviewRequest request(String fileId) {
    DataPreviewRequest request = new DataPreviewRequest();
    request.setFileId(fileId);
    return request;
  }

  private String exportCsv(String fileId) throws Exception {
    return exportToString(request(fileId), ExportFormat.CSV);
  }

  private String exportCsv(DataPreviewRequest request) throws Exception {
    return exportToString(request, ExportFormat.CSV);
  }

  private String exportToString(DataPreviewRequest request, ExportFormat format) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    dataService.exportData(dataService.prepareExport(request, format), out);
    return out.toString(StandardCharsets.UTF_8);
  }
}
