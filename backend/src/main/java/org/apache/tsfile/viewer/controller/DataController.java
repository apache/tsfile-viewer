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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.apache.tsfile.viewer.dto.ChartDataRequest;
import org.apache.tsfile.viewer.dto.ChartDataResponse;
import org.apache.tsfile.viewer.dto.DataPreviewRequest;
import org.apache.tsfile.viewer.dto.DataPreviewResponse;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.QueryTimeoutException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.service.DataService;

import jakarta.validation.Valid;

/**
 * REST controller for data query operations.
 *
 * <p>Provides endpoints for:
 *
 * <ul>
 *   <li>POST /api/data/preview - Paginated data preview with filtering
 *   <li>POST /api/data/query - Chart data with aggregation
 * </ul>
 *
 * <p>Validates: Requirement 8.4, 8.5 (Data endpoints)
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

  private static final Logger logger = LoggerFactory.getLogger(DataController.class);

  private final DataService dataService;

  public DataController(DataService dataService) {
    this.dataService = dataService;
  }

  /**
   * Previews data from a TsFile with filtering and pagination.
   *
   * @param request the data preview request
   * @return DataPreviewResponse with paginated data
   */
  @PostMapping("/preview")
  public ResponseEntity<DataPreviewResponse> previewData(
      @RequestBody @Valid DataPreviewRequest request)
      throws TsFileNotFoundException, AccessDeniedException, IOException, QueryTimeoutException {
    logger.debug("Previewing data for fileId={}", request.getFileId());

    DataPreviewResponse response = dataService.previewData(request);
    return ResponseEntity.ok(response);
  }

  /**
   * Queries chart data from a TsFile with optional aggregation.
   *
   * @param request the chart data request
   * @return ChartDataResponse with series data
   */
  @PostMapping("/query")
  public ResponseEntity<ChartDataResponse> queryChartData(
      @RequestBody @Valid ChartDataRequest request)
      throws TsFileNotFoundException, AccessDeniedException, IOException, QueryTimeoutException {
    logger.debug("Querying chart data for fileId={}", request.getFileId());

    ChartDataResponse response = dataService.queryChartData(request);
    return ResponseEntity.ok(response);
  }
}
