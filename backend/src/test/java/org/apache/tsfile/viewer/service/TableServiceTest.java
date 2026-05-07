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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.tsfile.enums.ColumnCategory;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.apache.tsfile.viewer.dto.DeviceListResponse;
import org.apache.tsfile.viewer.dto.TableDataRequest;
import org.apache.tsfile.viewer.dto.TableDataResponse;
import org.apache.tsfile.viewer.dto.TableListResponse;
import org.apache.tsfile.viewer.dto.ValueRange;

/**
 * Unit tests for TableService.
 *
 * <p>Tests table-level operations including table listing, device listing, and table data querying.
 */
@ExtendWith(MockitoExtension.class)
class TableServiceTest {

  @Mock private FileService fileService;

  private TableService tableService;
  private Path tempDir;
  private File testTsFile;

  @BeforeEach
  void setUp() throws IOException {
    tableService = new TableService(fileService);
    tempDir = Files.createTempDirectory("tsfile-table-test");
    testTsFile = tempDir.resolve("test.tsfile").toFile();
  }

  @AfterEach
  void tearDown() throws IOException {
    if (testTsFile != null && testTsFile.exists()) {
      Files.deleteIfExists(testTsFile.toPath());
    }
    if (tempDir != null) {
      Files.deleteIfExists(tempDir);
    }
  }

  /** Creates a test TsFile with a single table. */
  private void createSingleTableTsFile(int rowCount) throws Exception {
    String tableName = "sensor_data";

    TableSchema tableSchema =
        new TableSchema(
            tableName,
            Arrays.asList("temperature", "humidity"),
            Arrays.asList(TSDataType.DOUBLE, TSDataType.INT32),
            Arrays.asList(ColumnCategory.FIELD, ColumnCategory.FIELD));

    Tablet tablet =
        new Tablet(
            Arrays.asList("temperature", "humidity"),
            Arrays.asList(TSDataType.DOUBLE, TSDataType.INT32));

    for (int row = 0; row < rowCount; row++) {
      tablet.addTimestamp(row, row * 1000L);
      tablet.addValue(row, "temperature", 20.0 + row * 0.5);
      tablet.addValue(row, "humidity", 50 + row);
    }

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(testTsFile).tableSchema(tableSchema).build()) {
      writer.write(tablet);
    }
  }

  /** Creates a test TsFile with multiple tables. */
  private void createMultiTableTsFile() throws Exception {
    // Create first table
    String tableName1 = "sensor_data";
    TableSchema tableSchema1 =
        new TableSchema(
            tableName1,
            Arrays.asList("temperature", "humidity"),
            Arrays.asList(TSDataType.DOUBLE, TSDataType.INT32),
            Arrays.asList(ColumnCategory.FIELD, ColumnCategory.FIELD));

    Tablet tablet1 =
        new Tablet(
            Arrays.asList("temperature", "humidity"),
            Arrays.asList(TSDataType.DOUBLE, TSDataType.INT32));

    for (int row = 0; row < 5; row++) {
      tablet1.addTimestamp(row, row * 1000L);
      tablet1.addValue(row, "temperature", 20.0 + row);
      tablet1.addValue(row, "humidity", 50 + row);
    }

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(testTsFile).tableSchema(tableSchema1).build()) {
      writer.write(tablet1);
    }
  }

  /** Creates a test TsFile with TAG columns for device identification. */
  private void createTsFileWithTags(int deviceCount, int rowsPerDevice) throws Exception {
    String tableName = "device_data";

    TableSchema tableSchema =
        new TableSchema(
            tableName,
            Arrays.asList("device_id", "temperature", "status"),
            Arrays.asList(TSDataType.STRING, TSDataType.DOUBLE, TSDataType.INT32),
            Arrays.asList(ColumnCategory.TAG, ColumnCategory.FIELD, ColumnCategory.FIELD));

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(testTsFile).tableSchema(tableSchema).build()) {

      for (int d = 0; d < deviceCount; d++) {
        Tablet tablet =
            new Tablet(
                Arrays.asList("device_id", "temperature", "status"),
                Arrays.asList(TSDataType.STRING, TSDataType.DOUBLE, TSDataType.INT32));

        for (int row = 0; row < rowsPerDevice; row++) {
          int index = tablet.getRowSize();
          long timestamp = d * 100000L + row * 1000L;
          tablet.addTimestamp(index, timestamp);
          tablet.addValue(index, "device_id", "device_" + d);
          tablet.addValue(index, "temperature", 20.0 + row * 0.5);
          tablet.addValue(index, "status", row % 2);
        }

        writer.write(tablet);
      }
    }
  }

  @Nested
  @DisplayName("getTableList tests")
  class GetTableListTests {

    @Test
    @DisplayName("Should return table list for single table TsFile")
    void shouldReturnTableListForSingleTableFile() throws Exception {
      createSingleTableTsFile(10);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      TableListResponse response = tableService.getTableList("testFileId");

      assertThat(response).isNotNull();
      assertThat(response.getTotalCount()).isEqualTo(1);
      assertThat(response.getTables()).hasSize(1);
      assertThat(response.getTables().get(0).getTableName()).isEqualTo("sensor_data");
      assertThat(response.getTables().get(0).getColumns())
          .containsExactlyInAnyOrder("temperature", "humidity");
    }

    @Test
    @DisplayName("Should return table with row count")
    void shouldReturnTableWithRowCount() throws Exception {
      createSingleTableTsFile(15);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      TableListResponse response = tableService.getTableList("testFileId");

      assertThat(response.getTables().get(0).getRowCount()).isEqualTo(15);
    }
  }

  @Nested
  @DisplayName("getDeviceList tests")
  class GetDeviceListTests {

    @Test
    @DisplayName("Should return device list for TsFile with TAG columns")
    void shouldReturnDeviceListForTsFileWithTags() throws Exception {
      createTsFileWithTags(3, 5);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      DeviceListResponse response = tableService.getDeviceList("testFileId", null);

      assertThat(response).isNotNull();
      assertThat(response.getTotalCount()).isEqualTo(3);
      assertThat(response.getDevices()).hasSize(3);
    }

    @Test
    @DisplayName("Should return single device for TsFile without TAG columns")
    void shouldReturnSingleDeviceForTsFileWithoutTags() throws Exception {
      createSingleTableTsFile(10);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      DeviceListResponse response = tableService.getDeviceList("testFileId", null);

      assertThat(response).isNotNull();
      // Without TAG columns, all rows belong to single "device" (table name)
      assertThat(response.getTotalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should filter devices by table name")
    void shouldFilterDevicesByTableName() throws Exception {
      createTsFileWithTags(3, 5);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      DeviceListResponse response = tableService.getDeviceList("testFileId", "device_data");

      assertThat(response).isNotNull();
      assertThat(response.getTotalCount()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("queryTableData tests")
  class QueryTableDataTests {

    @Test
    @DisplayName("Should query table data with pagination")
    void shouldQueryTableDataWithPagination() throws Exception {
      createSingleTableTsFile(20);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      TableDataRequest request = new TableDataRequest();
      request.setFileId("testFileId");
      request.setTableName("sensor_data");
      request.setLimit(10);
      request.setOffset(0);

      TableDataResponse response = tableService.queryTableData(request);

      assertThat(response).isNotNull();
      assertThat(response.getTableName()).isEqualTo("sensor_data");
      assertThat(response.getRows()).hasSize(10);
      assertThat(response.getTotal()).isEqualTo(20);
      assertThat(response.isHasMore()).isTrue();
    }

    @Test
    @DisplayName("Should query table data with offset")
    void shouldQueryTableDataWithOffset() throws Exception {
      createSingleTableTsFile(20);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      TableDataRequest request = new TableDataRequest();
      request.setFileId("testFileId");
      request.setTableName("sensor_data");
      request.setLimit(10);
      request.setOffset(15);

      TableDataResponse response = tableService.queryTableData(request);

      assertThat(response).isNotNull();
      assertThat(response.getRows()).hasSize(5);
      assertThat(response.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("Should query table data with time range filter")
    void shouldQueryTableDataWithTimeRangeFilter() throws Exception {
      createSingleTableTsFile(20);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      TableDataRequest request = new TableDataRequest();
      request.setFileId("testFileId");
      request.setTableName("sensor_data");
      request.setStartTime(5000L); // Skip first 5 rows (0-4000ms)
      request.setEndTime(14000L); // Include rows 5-14
      request.setLimit(100);
      request.setOffset(0);

      TableDataResponse response = tableService.queryTableData(request);

      assertThat(response).isNotNull();
      assertThat(response.getRows()).hasSize(10); // rows with timestamps 5000-14000
    }

    @Test
    @DisplayName("Should query table data with specific columns")
    void shouldQueryTableDataWithSpecificColumns() throws Exception {
      createSingleTableTsFile(10);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      TableDataRequest request = new TableDataRequest();
      request.setFileId("testFileId");
      request.setTableName("sensor_data");
      request.setColumns(Arrays.asList("temperature"));
      request.setLimit(100);
      request.setOffset(0);

      TableDataResponse response = tableService.queryTableData(request);

      assertThat(response).isNotNull();
      assertThat(response.getColumns()).containsExactly("time", "temperature");
      assertThat(response.getRows().get(0)).containsKeys("time", "temperature");
      assertThat(response.getRows().get(0)).doesNotContainKey("humidity");
    }

    @Test
    @DisplayName("Should return column types in response")
    void shouldReturnColumnTypesInResponse() throws Exception {
      createSingleTableTsFile(5);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      TableDataRequest request = new TableDataRequest();
      request.setFileId("testFileId");
      request.setTableName("sensor_data");
      request.setLimit(10);
      request.setOffset(0);

      TableDataResponse response = tableService.queryTableData(request);

      assertThat(response).isNotNull();
      assertThat(response.getColumnTypes()).contains("TIMESTAMP", "DOUBLE", "INT32");
    }

    @Test
    @DisplayName("Should query table data with value range filter")
    void shouldQueryTableDataWithValueRangeFilter() throws Exception {
      createSingleTableTsFile(20);
      when(fileService.getFilePath(anyString())).thenReturn(testTsFile.getAbsolutePath());

      TableDataRequest request = new TableDataRequest();
      request.setFileId("testFileId");
      request.setTableName("sensor_data");
      request.setValueRange(new ValueRange(22.0, 25.0)); // temperature range
      request.setLimit(100);
      request.setOffset(0);

      TableDataResponse response = tableService.queryTableData(request);

      assertThat(response).isNotNull();
      // Only rows with temperature between 22.0 and 25.0 should be returned
      // Temperature starts at 20.0 and increases by 0.5 per row
      // 22.0 is at row 4, 25.0 is at row 10 (temperature = 20.0 + row * 0.5)
      assertThat(response.getRows().size()).isLessThanOrEqualTo(20);
    }
  }
}
