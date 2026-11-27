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

package org.apache.tsfile.viewer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.tsfile.viewer.dto.DeviceListResponse;
import org.apache.tsfile.viewer.dto.DeviceListResponse.DeviceInfo;
import org.apache.tsfile.viewer.dto.TableDataRequest;
import org.apache.tsfile.viewer.dto.TableDataResponse;
import org.apache.tsfile.viewer.dto.TableListResponse;
import org.apache.tsfile.viewer.dto.TableListResponse.TableInfo;
import org.apache.tsfile.viewer.service.TableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for TableController.
 *
 * <p>Tests REST API endpoints for table-level operations.
 */
class TableControllerTest {

  private TableController controller;
  private TableService tableService;

  @BeforeEach
  void setUp() {
    tableService = mock(TableService.class);
    controller = new TableController(tableService);
  }

  @Nested
  @DisplayName("GET /api/tables/{fileId}")
  class GetTableListTests {

    @Test
    @DisplayName("Should return table list successfully")
    void shouldReturnTableListSuccessfully() throws Exception {
      TableInfo tableInfo =
          TableInfo.builder()
              .tableName("sensor_data")
              .columns(Arrays.asList("temperature", "humidity"))
              .tagColumns(Arrays.asList())
              .fieldColumns(Arrays.asList("temperature", "humidity"))
              .rowCount(100)
              .build();

      TableListResponse mockResponse =
          TableListResponse.builder().tables(Arrays.asList(tableInfo)).totalCount(1).build();

      when(tableService.getTableList(anyString())).thenReturn(mockResponse);

      ResponseEntity<TableListResponse> response = controller.getTableList("testFileId");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalCount()).isEqualTo(1);
      assertThat(response.getBody().getTables()).hasSize(1);
      assertThat(response.getBody().getTables().get(0).getTableName()).isEqualTo("sensor_data");
      assertThat(response.getBody().getTables().get(0).getRowCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should return multiple tables")
    void shouldReturnMultipleTables() throws Exception {
      TableInfo table1 =
          TableInfo.builder()
              .tableName("sensor_data")
              .columns(Arrays.asList("temperature"))
              .tagColumns(Arrays.asList())
              .fieldColumns(Arrays.asList("temperature"))
              .rowCount(50)
              .build();

      TableInfo table2 =
          TableInfo.builder()
              .tableName("device_status")
              .columns(Arrays.asList("status"))
              .tagColumns(Arrays.asList())
              .fieldColumns(Arrays.asList("status"))
              .rowCount(30)
              .build();

      TableListResponse mockResponse =
          TableListResponse.builder().tables(Arrays.asList(table1, table2)).totalCount(2).build();

      when(tableService.getTableList(anyString())).thenReturn(mockResponse);

      ResponseEntity<TableListResponse> response = controller.getTableList("testFileId");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalCount()).isEqualTo(2);
      assertThat(response.getBody().getTables()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("GET /api/tables/{fileId}/devices")
  class GetDeviceListTests {

    @Test
    @DisplayName("Should return device list successfully")
    void shouldReturnDeviceListSuccessfully() throws Exception {
      DeviceInfo device1 =
          DeviceInfo.builder()
              .deviceId("sensor_data.device_001")
              .tableName("sensor_data")
              .tagValues(Arrays.asList("device_001"))
              .dataPointCount(100)
              .build();

      DeviceInfo device2 =
          DeviceInfo.builder()
              .deviceId("sensor_data.device_002")
              .tableName("sensor_data")
              .tagValues(Arrays.asList("device_002"))
              .dataPointCount(80)
              .build();

      DeviceListResponse mockResponse =
          DeviceListResponse.builder()
              .devices(Arrays.asList(device1, device2))
              .totalCount(2)
              .build();

      when(tableService.getDeviceList(anyString(), any())).thenReturn(mockResponse);

      ResponseEntity<DeviceListResponse> response = controller.getDeviceList("testFileId", null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalCount()).isEqualTo(2);
      assertThat(response.getBody().getDevices()).hasSize(2);
      assertThat(response.getBody().getDevices().get(0).getDeviceId())
          .isEqualTo("sensor_data.device_001");
    }

    @Test
    @DisplayName("Should filter devices by table name")
    void shouldFilterDevicesByTableName() throws Exception {
      DeviceInfo device =
          DeviceInfo.builder()
              .deviceId("sensor_data.device_001")
              .tableName("sensor_data")
              .tagValues(Arrays.asList("device_001"))
              .dataPointCount(100)
              .build();

      DeviceListResponse mockResponse =
          DeviceListResponse.builder().devices(Arrays.asList(device)).totalCount(1).build();

      when(tableService.getDeviceList(anyString(), anyString())).thenReturn(mockResponse);

      ResponseEntity<DeviceListResponse> response =
          controller.getDeviceList("testFileId", "sensor_data");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalCount()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("POST /api/tables/query")
  class QueryTableDataTests {

    @Test
    @DisplayName("Should query table data successfully")
    void shouldQueryTableDataSuccessfully() throws Exception {
      Map<String, Object> row1 = new HashMap<>();
      row1.put("time", 1000L);
      row1.put("temperature", 25.5);
      row1.put("humidity", 60);

      Map<String, Object> row2 = new HashMap<>();
      row2.put("time", 2000L);
      row2.put("temperature", 26.0);
      row2.put("humidity", 62);

      TableDataResponse mockResponse =
          TableDataResponse.builder()
              .tableName("sensor_data")
              .columns(Arrays.asList("time", "temperature", "humidity"))
              .columnTypes(Arrays.asList("TIMESTAMP", "DOUBLE", "INT32"))
              .rows(Arrays.asList(row1, row2))
              .total(100)
              .limit(10)
              .offset(0)
              .hasMore(true)
              .build();

      when(tableService.queryTableData(any(TableDataRequest.class))).thenReturn(mockResponse);

      TableDataRequest request = new TableDataRequest();
      request.setFileId("testFileId");
      request.setTableName("sensor_data");
      request.setLimit(10);
      request.setOffset(0);

      ResponseEntity<TableDataResponse> response = controller.queryTableData(request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTableName()).isEqualTo("sensor_data");
      assertThat(response.getBody().getTotal()).isEqualTo(100);
      assertThat(response.getBody().getRows()).hasSize(2);
      assertThat(response.getBody().isHasMore()).isTrue();
    }

    @Test
    @DisplayName("Should query with time range filter")
    void shouldQueryWithTimeRangeFilter() throws Exception {
      TableDataResponse mockResponse =
          TableDataResponse.builder()
              .tableName("sensor_data")
              .columns(Arrays.asList("time", "temperature"))
              .columnTypes(Arrays.asList("TIMESTAMP", "DOUBLE"))
              .rows(Arrays.asList())
              .total(0)
              .limit(10)
              .offset(0)
              .hasMore(false)
              .build();

      when(tableService.queryTableData(any(TableDataRequest.class))).thenReturn(mockResponse);

      TableDataRequest request = new TableDataRequest();
      request.setFileId("testFileId");
      request.setTableName("sensor_data");
      request.setStartTime(1000L);
      request.setEndTime(5000L);
      request.setLimit(10);
      request.setOffset(0);

      ResponseEntity<TableDataResponse> response = controller.queryTableData(request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTableName()).isEqualTo("sensor_data");
    }

    @Test
    @DisplayName("Should return empty response for empty table")
    void shouldReturnEmptyResponseForEmptyTable() throws Exception {
      TableDataResponse mockResponse =
          TableDataResponse.builder()
              .tableName("empty_table")
              .columns(Arrays.asList("time"))
              .columnTypes(Arrays.asList("TIMESTAMP"))
              .rows(Arrays.asList())
              .total(0)
              .limit(10)
              .offset(0)
              .hasMore(false)
              .build();

      when(tableService.queryTableData(any(TableDataRequest.class))).thenReturn(mockResponse);

      TableDataRequest request = new TableDataRequest();
      request.setFileId("testFileId");
      request.setTableName("empty_table");
      request.setLimit(10);
      request.setOffset(0);

      ResponseEntity<TableDataResponse> response = controller.queryTableData(request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotal()).isEqualTo(0);
      assertThat(response.getBody().getRows()).isEmpty();
    }
  }
}
