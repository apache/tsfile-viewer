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

import org.apache.tsfile.viewer.dto.ChartDataRequest;
import org.apache.tsfile.viewer.dto.ChartDataResponse;
import org.apache.tsfile.viewer.dto.DataPreviewRequest;
import org.apache.tsfile.viewer.dto.DataPreviewResponse;
import org.apache.tsfile.viewer.dto.ExportFormat;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.QueryTimeoutException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * REST controller for data query operations.
 *
 * <p>Provides endpoints for:
 *
 * <ul>
 *   <li>POST /api/data/preview - Paginated data preview with filtering
 *   <li>POST /api/data/export - Streaming export of all rows matching the filters
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
   * Exports every row matching the given filters as a downloadable CSV or JSON file.
   *
   * <p>The request body is the preview request, so filtering behaves exactly like the preview; its
   * {@code limit}/{@code offset} are ignored and the response is streamed page by page, which means
   * an export is neither limited to the current preview page nor to {@code
   * tsfile.query.max-result-size}.
   *
   * @param request the data preview request carrying the filters to apply
   * @param format {@code csv} or {@code json}
   * @param response the response the export is streamed to
   */
  @PostMapping("/export")
  public void exportData(
      @RequestBody @Valid DataPreviewRequest request,
      @RequestParam(name = "format", defaultValue = "csv") String format,
      HttpServletResponse response)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    ExportFormat exportFormat = ExportFormat.from(format);
    logger.debug("Exporting data for fileId={} as {}", request.getFileId(), exportFormat);

    // Resolve the file (and the CSV columns) before committing the response, so an unknown fileId
    // (404) or a forbidden path (403) still yields the regular JSON error body instead of a broken
    // download.
    DataService.ExportPlan plan = dataService.prepareExport(request, exportFormat);

    response.setContentType(exportFormat.getContentType());
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, exportFormat.getContentDisposition());

    long startTime = System.currentTimeMillis();
    try {
      long rows = dataService.exportData(plan, response.getOutputStream());
      logger.info(
          "Exported {} rows as {} for fileId={} in {} ms",
          rows,
          exportFormat,
          request.getFileId(),
          System.currentTimeMillis() - startTime);
    } catch (IOException e) {
      // Part of the body may already be on the wire (client gone, disk error while reading), so the
      // response can only be turned into an error when nothing has been committed yet.
      logger.error(
          "Export failed for fileId={} as {} after {} ms",
          request.getFileId(),
          exportFormat,
          System.currentTimeMillis() - startTime,
          e);
      if (!response.isCommitted()) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Export failed: " + e.getMessage(), e);
      }
    }
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
