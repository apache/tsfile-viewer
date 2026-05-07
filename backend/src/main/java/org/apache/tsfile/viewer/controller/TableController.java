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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.apache.tsfile.viewer.dto.DeviceListResponse;
import org.apache.tsfile.viewer.dto.TableDataRequest;
import org.apache.tsfile.viewer.dto.TableDataResponse;
import org.apache.tsfile.viewer.dto.TableListResponse;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.service.TableService;

import jakarta.validation.Valid;

/**
 * REST controller for table-level operations.
 *
 * <p>Provides endpoints for:
 *
 * <ul>
 *   <li>GET /api/tables/{fileId} - Get list of tables in a TsFile
 *   <li>GET /api/tables/{fileId}/devices - Get list of devices in a TsFile
 *   <li>POST /api/tables/query - Query data from a specific table
 * </ul>
 *
 * <p>These endpoints support multi-table TsFile scenarios where a single file may contain multiple
 * tables with different schemas and devices.
 */
@RestController
@RequestMapping("/api/tables")
public class TableController {

  private static final Logger logger = LoggerFactory.getLogger(TableController.class);

  private final TableService tableService;

  public TableController(TableService tableService) {
    this.tableService = tableService;
  }

  /**
   * Gets a list of all tables in the specified TsFile.
   *
   * <p>Returns table names, column information (including TAG and FIELD columns), and row counts
   * for each table.
   *
   * @param fileId the file identifier
   * @return TableListResponse with table information
   */
  @GetMapping("/{fileId}")
  public ResponseEntity<TableListResponse> getTableList(@PathVariable String fileId)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting table list for fileId={}", fileId);

    TableListResponse response = tableService.getTableList(fileId);
    return ResponseEntity.ok(response);
  }

  /**
   * Gets a list of all unique devices in the specified TsFile.
   *
   * <p>Returns device identifiers, associated table names, TAG values, and data point counts.
   * Optionally filter by table name.
   *
   * @param fileId the file identifier
   * @param tableName optional table name filter
   * @return DeviceListResponse with device information
   */
  @GetMapping("/{fileId}/devices")
  public ResponseEntity<DeviceListResponse> getDeviceList(
      @PathVariable String fileId, @RequestParam(required = false) String tableName)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting device list for fileId={}, tableName={}", fileId, tableName);

    DeviceListResponse response = tableService.getDeviceList(fileId, tableName);
    return ResponseEntity.ok(response);
  }

  /**
   * Queries data from a specific table with pagination.
   *
   * <p>Supports filtering by time range, specific columns, and value range. Returns paginated
   * results with column information.
   *
   * @param request the table data request
   * @return TableDataResponse with paginated data
   */
  @PostMapping("/query")
  public ResponseEntity<TableDataResponse> queryTableData(
      @RequestBody @Valid TableDataRequest request)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Querying table {} for fileId={}", request.getTableName(), request.getFileId());

    TableDataResponse response = tableService.queryTableData(request);
    return ResponseEntity.ok(response);
  }
}
