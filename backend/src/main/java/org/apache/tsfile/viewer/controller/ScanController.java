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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.apache.tsfile.viewer.dto.DirectoryScanRequest;
import org.apache.tsfile.viewer.dto.FileScanRequest;
import org.apache.tsfile.viewer.dto.ScanReportDTO;
import org.apache.tsfile.viewer.dto.ScanResultDTO;
import org.apache.tsfile.viewer.dto.ScanTaskDTO;
import org.apache.tsfile.viewer.service.ScanService;

import jakarta.validation.Valid;

/**
 * REST controller for TsFile scan operations.
 *
 * <p>Provides endpoints for:
 *
 * <ul>
 *   <li>POST /api/scan/directory - Start a directory scan
 *   <li>POST /api/scan/file - Single file health check
 *   <li>GET /api/scan/status/{taskId} - Query task status
 *   <li>GET /api/scan/report/{taskId} - Get scan report (paginated)
 *   <li>DELETE /api/scan/{taskId} - Cancel a scan task
 *   <li>GET /api/scan/export/{taskId}?format=json|csv - Export scan report
 *   <li>GET /api/scan/stream/{taskId} - SSE event stream for real-time updates
 * </ul>
 *
 * <p>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 2.1, 3.3, 4.1, 4.2, 4.3, 6.1, 6.2, 6.3, 7.1, 8.5,
 * 9.4
 */
@RestController
@RequestMapping("/api/scan")
public class ScanController {

  private static final Logger logger = LoggerFactory.getLogger(ScanController.class);

  private final ScanService scanService;

  public ScanController(ScanService scanService) {
    this.scanService = scanService;
  }

  /**
   * Starts an asynchronous directory scan.
   *
   * <p>Validates the directory path, creates a scan task, and returns the task ID along with the
   * queue position. The scan executes asynchronously in the background.
   *
   * @param request the directory scan request containing the directory path
   * @return response with taskId and queuePosition
   */
  @PostMapping("/directory")
  public ResponseEntity<Map<String, Object>> startDirectoryScan(
      @RequestBody @Valid DirectoryScanRequest request) {
    logger.info("Starting directory scan for path: {}", request.directoryPath());

    String taskId = scanService.startDirectoryScan(request.directoryPath());

    // Retrieve the task to get the queue position
    ScanTaskDTO taskStatus = scanService.getTaskStatus(taskId);

    Map<String, Object> response =
        Map.of("taskId", taskId, "queuePosition", taskStatus.queuePosition());

    logger.info(
        "Directory scan task created: taskId={}, queuePosition={}",
        taskId,
        taskStatus.queuePosition());

    return ResponseEntity.ok(response);
  }

  /**
   * Performs a synchronous health check on a single TsFile.
   *
   * <p>Validates the file path and runs a full health check, returning the result immediately.
   *
   * @param request the file scan request containing the file path
   * @return the scan result for the file
   */
  @PostMapping("/file")
  public ResponseEntity<ScanResultDTO> checkSingleFile(
      @RequestBody @Valid FileScanRequest request) {
    logger.info("Starting single file health check: {}", request.filePath());

    ScanResultDTO result = scanService.checkSingleFile(request.filePath());

    logger.info(
        "Single file health check completed: path={}, status={}",
        request.filePath(),
        result.healthStatus());

    return ResponseEntity.ok(result);
  }

  /**
   * Gets the current status of a scan task.
   *
   * @param taskId the task ID to query
   * @return the task status
   */
  @GetMapping("/status/{taskId}")
  public ResponseEntity<ScanTaskDTO> getTaskStatus(@PathVariable String taskId) {
    logger.debug("Querying task status: taskId={}", taskId);

    ScanTaskDTO taskStatus = scanService.getTaskStatus(taskId);

    return ResponseEntity.ok(taskStatus);
  }

  /**
   * Gets the scan report for a completed task with paginated results.
   *
   * @param taskId the task ID to get the report for
   * @param page the page number (zero-based, default 0)
   * @param size the page size (default 50)
   * @return the scan report with paginated results
   */
  @GetMapping("/report/{taskId}")
  public ResponseEntity<ScanReportDTO> getReport(
      @PathVariable String taskId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0, got: " + page);
    }
    if (size < 1 || size > 1000) {
      throw new IllegalArgumentException("size must be between 1 and 1000, got: " + size);
    }
    logger.debug("Getting scan report: taskId={}, page={}, size={}", taskId, page, size);

    ScanReportDTO report = scanService.getReport(taskId, page, size);

    return ResponseEntity.ok(report);
  }

  /**
   * Cancels a scan task.
   *
   * <p>If the task is already in a terminal state (COMPLETED, CANCELLED, FAILED), this operation is
   * a no-op but still returns a success message.
   *
   * @param taskId the task ID to cancel
   * @return a message confirming the cancellation
   */
  @DeleteMapping("/{taskId}")
  public ResponseEntity<Map<String, String>> cancelTask(@PathVariable String taskId) {
    logger.info("Cancelling scan task: taskId={}", taskId);

    scanService.cancelTask(taskId);

    return ResponseEntity.ok(Map.of("message", "Scan task cancelled: " + taskId));
  }

  /**
   * Exports a scan report as a downloadable file.
   *
   * <p>Supports JSON and CSV formats. Sets the {@code Content-Disposition} header for file download
   * with filename {@code scan-report-{taskId}.{ext}}.
   *
   * <p>Validates: Requirements 4.1, 4.2, 4.3
   *
   * @param taskId the task ID to export the report for
   * @param format the export format ("json" or "csv", default "json")
   * @return the exported report as a downloadable file
   */
  @GetMapping("/export/{taskId}")
  public ResponseEntity<byte[]> exportReport(
      @PathVariable String taskId, @RequestParam(defaultValue = "json") String format) {
    logger.info("Exporting scan report: taskId={}, format={}", taskId, format);

    byte[] data = scanService.exportReport(taskId, format);

    String extension = format.equalsIgnoreCase("csv") ? "csv" : "json";
    String contentType =
        format.equalsIgnoreCase("csv") ? "text/csv; charset=UTF-8" : "application/json";
    String filename = "scan-report-" + taskId + "." + extension;

    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .header("Content-Type", contentType)
        .body(data);
  }

  /**
   * Opens an SSE event stream for real-time scan updates.
   *
   * <p>Returns an {@link SseEmitter} that pushes the following event types:
   *
   * <ul>
   *   <li>{@code progress} — scan progress (scanned count, total, current file, percentage)
   *   <li>{@code log} — scan log messages (timestamp, level, message)
   *   <li>{@code error-found} — file error discovered (file path, error type, severity)
   *   <li>{@code complete} — scan completed (task ID, duration, file counts)
   *   <li>{@code status-change} — task status transition (old status, new status)
   * </ul>
   *
   * <p>The SSE connection has a 30-minute timeout. When the connection is dropped (client
   * disconnect, timeout, or error), the emitter is automatically cleaned up and the backend scan
   * continues unaffected.
   *
   * <p>Validates: Requirements 1.3, 3.3, 6.1, 6.2, 6.3, 9.4
   *
   * @param taskId the task ID to subscribe to
   * @return an SseEmitter for real-time event streaming
   */
  @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamEvents(@PathVariable String taskId) {
    logger.info("SSE stream requested for task: {}", taskId);

    SseEmitter emitter = scanService.registerEmitter(taskId);

    logger.info("SSE stream established for task: {}", taskId);

    return emitter;
  }
}
