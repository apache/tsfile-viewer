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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.tsfile.viewer.dto.ErrorSeverity;
import org.apache.tsfile.viewer.dto.HealthStatus;
import org.apache.tsfile.viewer.dto.ScanErrorType;
import org.apache.tsfile.viewer.dto.ScanReportDTO;
import org.apache.tsfile.viewer.dto.ScanResultDTO;
import org.apache.tsfile.viewer.dto.ScanTaskDTO;
import org.apache.tsfile.viewer.dto.ScanTaskStatus;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.TaskNotFoundException;
import org.apache.tsfile.viewer.tsfile.TsFileHealthChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * Unit tests for {@link ScanService}.
 *
 * <p>Validates: Requirements 1.1, 1.2, 1.4, 1.5, 2.1, 7.1
 */
@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

  @Mock private Cache<String, ScanTask> scanTaskCache;
  @Mock private PathValidationService pathValidationService;
  @Mock private TsFileHealthChecker tsFileHealthChecker;
  @Mock private ScanTaskExecutor scanTaskExecutor;

  private ScanService scanService;

  @BeforeEach
  void setUp() {
    scanService =
        new ScanService(
            scanTaskCache, pathValidationService, tsFileHealthChecker, scanTaskExecutor);
  }

  @Nested
  @DisplayName("generateTaskId tests")
  class GenerateTaskIdTests {

    @Test
    @DisplayName("Should generate task ID matching expected format")
    void shouldGenerateTaskIdMatchingFormat() {
      String taskId = scanService.generateTaskId();

      assertThat(taskId).matches("^\\d{14}-[a-z0-9]{6}$");
    }

    @Test
    @DisplayName("Should generate unique task IDs")
    void shouldGenerateUniqueTaskIds() {
      List<String> ids = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        ids.add(scanService.generateTaskId());
      }

      assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Task ID timestamp part should be 14 digits")
    void taskIdTimestampShouldBe14Digits() {
      String taskId = scanService.generateTaskId();
      String timestampPart = taskId.split("-")[0];

      assertThat(timestampPart).hasSize(14);
      assertThat(timestampPart).matches("^\\d{14}$");
    }

    @Test
    @DisplayName("Task ID random part should be 6 lowercase alphanumeric chars")
    void taskIdRandomPartShouldBe6LowercaseAlphanumeric() {
      String taskId = scanService.generateTaskId();
      String randomPart = taskId.split("-")[1];

      assertThat(randomPart).hasSize(6);
      assertThat(randomPart).matches("^[a-z0-9]{6}$");
    }
  }

  @Nested
  @DisplayName("startDirectoryScan tests")
  class StartDirectoryScanTests {

    @Test
    @DisplayName("Should create task and return task ID for valid path")
    void shouldCreateTaskAndReturnTaskId() {
      when(pathValidationService.isPathAllowed("/data/tsfiles")).thenReturn(true);
      when(scanTaskCache.asMap()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
      doNothing().when(scanTaskExecutor).executeScan(any(ScanTask.class));

      String taskId = scanService.startDirectoryScan("/data/tsfiles");

      assertThat(taskId).matches("^\\d{14}-[a-z0-9]{6}$");
      verify(scanTaskCache).put(any(String.class), any(ScanTask.class));
      verify(scanTaskExecutor).executeScan(any(ScanTask.class));
    }

    @Test
    @DisplayName("Should throw AccessDeniedException for path not in whitelist")
    void shouldThrowAccessDeniedForInvalidPath() {
      when(pathValidationService.isPathAllowed("/etc/secret")).thenReturn(false);

      assertThatThrownBy(() -> scanService.startDirectoryScan("/etc/secret"))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Access denied");

      verify(scanTaskCache, never()).put(any(), any());
      verify(scanTaskExecutor, never()).executeScan(any());
    }

    @Test
    @DisplayName("Created task should have QUEUED status")
    void createdTaskShouldHaveQueuedStatus() {
      when(pathValidationService.isPathAllowed("/data/tsfiles")).thenReturn(true);
      when(scanTaskCache.asMap()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
      doNothing().when(scanTaskExecutor).executeScan(any(ScanTask.class));

      scanService.startDirectoryScan("/data/tsfiles");

      verify(scanTaskCache)
          .put(
              any(String.class),
              org.mockito.ArgumentMatchers.argThat(task ->
                  task.getStatus() == ScanTaskStatus.QUEUED
                      && task.getTargetPath().equals("/data/tsfiles")));
    }
  }

  @Nested
  @DisplayName("checkSingleFile tests")
  class CheckSingleFileTests {

    @Test
    @DisplayName("Should return scan result for valid file")
    void shouldReturnScanResultForValidFile() {
      when(pathValidationService.isPathAllowed("/data/tsfiles/test.tsfile")).thenReturn(true);

      ScanResult mockResult =
          new ScanResult("/data/tsfiles/test.tsfile", 1024L, HealthStatus.HEALTHY, List.of(), 50L);
      when(tsFileHealthChecker.check(Paths.get("/data/tsfiles/test.tsfile")))
          .thenReturn(mockResult);

      ScanResultDTO result = scanService.checkSingleFile("/data/tsfiles/test.tsfile");

      assertThat(result.filePath()).isEqualTo("/data/tsfiles/test.tsfile");
      assertThat(result.fileSize()).isEqualTo(1024L);
      assertThat(result.healthStatus()).isEqualTo(HealthStatus.HEALTHY);
      assertThat(result.errors()).isEmpty();
      assertThat(result.scanDurationMs()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should return scan result with errors for unhealthy file")
    void shouldReturnScanResultWithErrors() {
      when(pathValidationService.isPathAllowed("/data/tsfiles/bad.tsfile")).thenReturn(true);

      FileError error =
          new FileError(
              ScanErrorType.STRUCTURE_CORRUPT,
              "File structure",
              "Structure check failed",
              ErrorSeverity.ERROR);
      ScanResult mockResult =
          new ScanResult(
              "/data/tsfiles/bad.tsfile", 2048L, HealthStatus.ERROR, List.of(error), 100L);
      when(tsFileHealthChecker.check(Paths.get("/data/tsfiles/bad.tsfile"))).thenReturn(mockResult);

      ScanResultDTO result = scanService.checkSingleFile("/data/tsfiles/bad.tsfile");

      assertThat(result.healthStatus()).isEqualTo(HealthStatus.ERROR);
      assertThat(result.errors()).hasSize(1);
      assertThat(result.errors().get(0).errorType()).isEqualTo(ScanErrorType.STRUCTURE_CORRUPT);
      assertThat(result.errors().get(0).severity()).isEqualTo(ErrorSeverity.ERROR);
    }

    @Test
    @DisplayName("Should throw AccessDeniedException for path not in whitelist")
    void shouldThrowAccessDeniedForInvalidPath() {
      when(pathValidationService.isPathAllowed("/etc/secret.tsfile")).thenReturn(false);

      assertThatThrownBy(() -> scanService.checkSingleFile("/etc/secret.tsfile"))
          .isInstanceOf(AccessDeniedException.class);

      verify(tsFileHealthChecker, never()).check(any(Path.class));
    }
  }

  @Nested
  @DisplayName("getTaskStatus tests")
  class GetTaskStatusTests {

    @Test
    @DisplayName("Should return task status for existing task")
    void shouldReturnTaskStatusForExistingTask() {
      ScanTask task = new ScanTask("20260206142530-abc123", "/data/tsfiles");
      task.setStatus(ScanTaskStatus.RUNNING);
      task.setTotalFiles(10);
      task.setScannedFiles(3);
      task.setCurrentFile("data03.tsfile");
      when(scanTaskCache.getIfPresent("20260206142530-abc123")).thenReturn(task);

      ScanTaskDTO result = scanService.getTaskStatus("20260206142530-abc123");

      assertThat(result.taskId()).isEqualTo("20260206142530-abc123");
      assertThat(result.targetPath()).isEqualTo("/data/tsfiles");
      assertThat(result.status()).isEqualTo(ScanTaskStatus.RUNNING);
      assertThat(result.totalFiles()).isEqualTo(10);
      assertThat(result.scannedFiles()).isEqualTo(3);
      assertThat(result.currentFile()).isEqualTo("data03.tsfile");
    }

    @Test
    @DisplayName("Should throw TaskNotFoundException for non-existent task")
    void shouldThrowTaskNotFoundForNonExistentTask() {
      when(scanTaskCache.getIfPresent("nonexistent")).thenReturn(null);

      assertThatThrownBy(() -> scanService.getTaskStatus("nonexistent"))
          .isInstanceOf(TaskNotFoundException.class)
          .hasMessageContaining("nonexistent");
    }
  }

  @Nested
  @DisplayName("getReport tests")
  class GetReportTests {

    @Test
    @DisplayName("Should return paginated report with correct statistics")
    void shouldReturnPaginatedReportWithCorrectStatistics() {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      ScanReportDTO report = scanService.getReport("task-123", 0, 50);

      assertThat(report.taskId()).isEqualTo("task-123");
      assertThat(report.totalFiles()).isEqualTo(3);
      assertThat(report.healthyCount()).isEqualTo(1);
      assertThat(report.warningCount()).isEqualTo(1);
      assertThat(report.errorCount()).isEqualTo(1);
      assertThat(report.results()).hasSize(3);
      assertThat(report.currentPage()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should paginate results correctly")
    void shouldPaginateResultsCorrectly() {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      // Page 0, size 2 — should return first 2 results
      ScanReportDTO page0 = scanService.getReport("task-123", 0, 2);
      assertThat(page0.results()).hasSize(2);
      assertThat(page0.currentPage()).isEqualTo(0);
      assertThat(page0.totalPages()).isEqualTo(2);

      // Page 1, size 2 — should return last 1 result
      ScanReportDTO page1 = scanService.getReport("task-123", 1, 2);
      assertThat(page1.results()).hasSize(1);
      assertThat(page1.currentPage()).isEqualTo(1);
      assertThat(page1.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should clamp page beyond range to last valid page")
    void shouldClampPageBeyondRangeToLastValidPage() {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      ScanReportDTO report = scanService.getReport("task-123", 10, 50);

      // Page is clamped to last valid page (page 0), so all 3 results are returned
      assertThat(report.results()).hasSize(3);
      assertThat(report.currentPage()).isEqualTo(0);
      // Statistics should still reflect all results
      assertThat(report.totalFiles()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should calculate error type distribution correctly")
    void shouldCalculateErrorTypeDistributionCorrectly() {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      ScanReportDTO report = scanService.getReport("task-123", 0, 50);

      assertThat(report.errorTypeDistribution())
          .containsEntry(ScanErrorType.DATA_READ_ERROR, 1)
          .containsEntry(ScanErrorType.STRUCTURE_CORRUPT, 1);
    }

    @Test
    @DisplayName("Should handle empty results")
    void shouldHandleEmptyResults() {
      ScanTask task = new ScanTask("task-empty", "/data/empty");
      task.setStartTime(LocalDateTime.now());
      task.setEndTime(LocalDateTime.now());
      when(scanTaskCache.getIfPresent("task-empty")).thenReturn(task);

      ScanReportDTO report = scanService.getReport("task-empty", 0, 50);

      assertThat(report.totalFiles()).isEqualTo(0);
      assertThat(report.healthyCount()).isEqualTo(0);
      assertThat(report.warningCount()).isEqualTo(0);
      assertThat(report.errorCount()).isEqualTo(0);
      assertThat(report.results()).isEmpty();
      assertThat(report.totalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should throw TaskNotFoundException for non-existent task")
    void shouldThrowTaskNotFoundForNonExistentTask() {
      when(scanTaskCache.getIfPresent("nonexistent")).thenReturn(null);

      assertThatThrownBy(() -> scanService.getReport("nonexistent", 0, 50))
          .isInstanceOf(TaskNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("cancelTask tests")
  class CancelTaskTests {

    @Test
    @DisplayName("Should cancel a QUEUED task")
    void shouldCancelQueuedTask() {
      ScanTask task = new ScanTask("task-cancel", "/data/tsfiles");
      task.setStatus(ScanTaskStatus.QUEUED);
      when(scanTaskCache.getIfPresent("task-cancel")).thenReturn(task);

      scanService.cancelTask("task-cancel");

      assertThat(task.getStatus()).isEqualTo(ScanTaskStatus.CANCELLED);
      assertThat(task.getEndTime()).isNotNull();
      verify(scanTaskCache).put("task-cancel", task);
    }

    @Test
    @DisplayName("Should cancel a RUNNING task")
    void shouldCancelRunningTask() {
      ScanTask task = new ScanTask("task-cancel", "/data/tsfiles");
      task.setStatus(ScanTaskStatus.RUNNING);
      when(scanTaskCache.getIfPresent("task-cancel")).thenReturn(task);

      scanService.cancelTask("task-cancel");

      assertThat(task.getStatus()).isEqualTo(ScanTaskStatus.CANCELLED);
      assertThat(task.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("Should not change status of already COMPLETED task")
    void shouldNotChangeCompletedTask() {
      ScanTask task = new ScanTask("task-done", "/data/tsfiles");
      task.setStatus(ScanTaskStatus.COMPLETED);
      when(scanTaskCache.getIfPresent("task-done")).thenReturn(task);

      scanService.cancelTask("task-done");

      assertThat(task.getStatus()).isEqualTo(ScanTaskStatus.COMPLETED);
      verify(scanTaskCache, never()).put(any(), any());
    }

    @Test
    @DisplayName("Should not change status of already CANCELLED task")
    void shouldNotChangeCancelledTask() {
      ScanTask task = new ScanTask("task-cancelled", "/data/tsfiles");
      task.setStatus(ScanTaskStatus.CANCELLED);
      when(scanTaskCache.getIfPresent("task-cancelled")).thenReturn(task);

      scanService.cancelTask("task-cancelled");

      assertThat(task.getStatus()).isEqualTo(ScanTaskStatus.CANCELLED);
      verify(scanTaskCache, never()).put(any(), any());
    }

    @Test
    @DisplayName("Should not change status of FAILED task")
    void shouldNotChangeFailedTask() {
      ScanTask task = new ScanTask("task-failed", "/data/tsfiles");
      task.setStatus(ScanTaskStatus.FAILED);
      when(scanTaskCache.getIfPresent("task-failed")).thenReturn(task);

      scanService.cancelTask("task-failed");

      assertThat(task.getStatus()).isEqualTo(ScanTaskStatus.FAILED);
      verify(scanTaskCache, never()).put(any(), any());
    }

    @Test
    @DisplayName("Should throw TaskNotFoundException for non-existent task")
    void shouldThrowTaskNotFoundForNonExistentTask() {
      when(scanTaskCache.getIfPresent("nonexistent")).thenReturn(null);

      assertThatThrownBy(() -> scanService.cancelTask("nonexistent"))
          .isInstanceOf(TaskNotFoundException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // Export report tests
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("exportReport tests")
  class ExportReportTests {

    @Test
    @DisplayName("Should export report as JSON with valid structure")
    void shouldExportReportAsJson() throws Exception {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      byte[] jsonBytes = scanService.exportReport("task-123", "json");

      assertThat(jsonBytes).isNotEmpty();

      // Parse JSON and verify structure
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonBytes);

      assertThat(root.get("taskId").asText()).isEqualTo("task-123");
      assertThat(root.get("totalFiles").asInt()).isEqualTo(3);
      assertThat(root.get("healthyCount").asInt()).isEqualTo(1);
      assertThat(root.get("warningCount").asInt()).isEqualTo(1);
      assertThat(root.get("errorCount").asInt()).isEqualTo(1);
      assertThat(root.get("results").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should export report as JSON with all results (not paginated)")
    void shouldExportAllResultsInJson() throws Exception {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      byte[] jsonBytes = scanService.exportReport("task-123", "json");

      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonBytes);

      // All 3 results should be present (not paginated)
      assertThat(root.get("results").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should export report as CSV with header and data rows")
    void shouldExportReportAsCsv() {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      byte[] csvBytes = scanService.exportReport("task-123", "csv");

      assertThat(csvBytes).isNotEmpty();

      String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
      String[] lines = csv.split("\r\n");

      // Header + 1 healthy (no errors, 1 row) + 1 warning (1 error, 1 row) + 1 error (1 error, 1
      // row) = 4 lines
      assertThat(lines).hasSize(4);

      // Verify header
      assertThat(lines[0])
          .isEqualTo(
              "filePath,fileSize,healthStatus,errorType,severity,location,description,scanDurationMs");
    }

    @Test
    @DisplayName("CSV should have correct columns for healthy file (empty error fields)")
    void csvShouldHaveEmptyErrorFieldsForHealthyFile() {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      byte[] csvBytes = scanService.exportReport("task-123", "csv");
      String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
      String[] lines = csv.split("\r\n");

      // First data row is the healthy file
      String healthyRow = lines[1];
      assertThat(healthyRow).contains("/data/tsfiles/good.tsfile");
      assertThat(healthyRow).contains("1024");
      assertThat(healthyRow).contains("HEALTHY");
      // Should have empty error fields (consecutive commas)
      assertThat(healthyRow).contains(",,,,");
    }

    @Test
    @DisplayName("CSV should produce one row per error for files with errors")
    void csvShouldProduceOneRowPerError() {
      ScanTask task = new ScanTask("task-multi", "/data");
      task.setStartTime(LocalDateTime.now().minusMinutes(1));
      task.setEndTime(LocalDateTime.now());

      FileError error1 =
          new FileError(
              ScanErrorType.STRUCTURE_CORRUPT, "Chunk #1", "Corrupt chunk", ErrorSeverity.ERROR);
      FileError error2 =
          new FileError(
              ScanErrorType.CHUNK_STATISTICS_MISMATCH,
              "Chunk #2",
              "Stats mismatch",
              ErrorSeverity.ERROR);
      ScanResult multiErrorResult =
          new ScanResult(
              "/data/multi.tsfile", 8192L, HealthStatus.ERROR, List.of(error1, error2), 200L);

      task.setResults(List.of(multiErrorResult));
      task.setTotalFiles(1);
      task.setScannedFiles(1);
      when(scanTaskCache.getIfPresent("task-multi")).thenReturn(task);

      byte[] csvBytes = scanService.exportReport("task-multi", "csv");
      String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
      String[] lines = csv.split("\r\n");

      // Header + 2 error rows = 3 lines
      assertThat(lines).hasSize(3);
      assertThat(lines[1]).contains("STRUCTURE_CORRUPT");
      assertThat(lines[2]).contains("CHUNK_STATISTICS_MISMATCH");
    }

    @Test
    @DisplayName("CSV should escape fields containing commas per RFC 4180")
    void csvShouldEscapeFieldsWithCommas() {
      assertThat(scanService.csvEscape("hello,world")).isEqualTo("\"hello,world\"");
    }

    @Test
    @DisplayName("CSV should escape fields containing double quotes per RFC 4180")
    void csvShouldEscapeFieldsWithDoubleQuotes() {
      assertThat(scanService.csvEscape("say \"hello\"")).isEqualTo("\"say \"\"hello\"\"\"");
    }

    @Test
    @DisplayName("CSV should escape fields containing newlines per RFC 4180")
    void csvShouldEscapeFieldsWithNewlines() {
      assertThat(scanService.csvEscape("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    @DisplayName("CSV should return empty string for null values")
    void csvShouldReturnEmptyStringForNull() {
      assertThat(scanService.csvEscape(null)).isEmpty();
    }

    @Test
    @DisplayName("CSV should not escape plain values")
    void csvShouldNotEscapePlainValues() {
      assertThat(scanService.csvEscape("simple")).isEqualTo("simple");
    }

    @Test
    @DisplayName("Should be case-insensitive for format parameter")
    void shouldBeCaseInsensitiveForFormat() {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      byte[] jsonUpper = scanService.exportReport("task-123", "JSON");
      byte[] csvUpper = scanService.exportReport("task-123", "CSV");

      assertThat(jsonUpper).isNotEmpty();
      assertThat(csvUpper).isNotEmpty();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for unsupported format")
    void shouldThrowForUnsupportedFormat() {
      ScanTask task = createTaskWithResults();
      when(scanTaskCache.getIfPresent("task-123")).thenReturn(task);

      assertThatThrownBy(() -> scanService.exportReport("task-123", "xml"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported export format");
    }

    @Test
    @DisplayName("Should throw TaskNotFoundException for non-existent task")
    void shouldThrowTaskNotFoundForNonExistentTask() {
      when(scanTaskCache.getIfPresent("nonexistent")).thenReturn(null);

      assertThatThrownBy(() -> scanService.exportReport("nonexistent", "json"))
          .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    @DisplayName("Should export empty report for task with no results")
    void shouldExportEmptyReport() throws Exception {
      ScanTask task = new ScanTask("task-empty", "/data/empty");
      task.setStartTime(LocalDateTime.now());
      task.setEndTime(LocalDateTime.now());
      when(scanTaskCache.getIfPresent("task-empty")).thenReturn(task);

      // JSON export
      byte[] jsonBytes = scanService.exportReport("task-empty", "json");
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonBytes);
      assertThat(root.get("totalFiles").asInt()).isEqualTo(0);
      assertThat(root.get("results").size()).isEqualTo(0);

      // CSV export — should only have header
      byte[] csvBytes = scanService.exportReport("task-empty", "csv");
      String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
      String[] lines = csv.split("\r\n");
      assertThat(lines).hasSize(1); // header only
    }
  }

  // ---------------------------------------------------------------------------
  // SSE emitter management tests
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("SSE emitter management tests")
  class SseEmitterTests {

    @Test
    @DisplayName("Should register emitter for existing task")
    void shouldRegisterEmitterForExistingTask() {
      ScanTask task = new ScanTask("task-sse", "/data/tsfiles");
      when(scanTaskCache.getIfPresent("task-sse")).thenReturn(task);

      var emitter = scanService.registerEmitter("task-sse");

      assertThat(emitter).isNotNull();
      assertThat(scanService.getEmitterCount("task-sse")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should register multiple emitters for same task")
    void shouldRegisterMultipleEmittersForSameTask() {
      ScanTask task = new ScanTask("task-sse", "/data/tsfiles");
      when(scanTaskCache.getIfPresent("task-sse")).thenReturn(task);

      scanService.registerEmitter("task-sse");
      scanService.registerEmitter("task-sse");
      scanService.registerEmitter("task-sse");

      assertThat(scanService.getEmitterCount("task-sse")).isEqualTo(3);
    }

    @Test
    @DisplayName(
        "Should throw TaskNotFoundException when registering emitter for non-existent task")
    void shouldThrowTaskNotFoundWhenRegisteringEmitterForNonExistentTask() {
      when(scanTaskCache.getIfPresent("nonexistent")).thenReturn(null);

      assertThatThrownBy(() -> scanService.registerEmitter("nonexistent"))
          .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    @DisplayName("Should remove emitter and clean up empty list")
    void shouldRemoveEmitterAndCleanUpEmptyList() {
      ScanTask task = new ScanTask("task-sse", "/data/tsfiles");
      when(scanTaskCache.getIfPresent("task-sse")).thenReturn(task);

      var emitter = scanService.registerEmitter("task-sse");
      assertThat(scanService.getEmitterCount("task-sse")).isEqualTo(1);

      scanService.removeEmitter("task-sse", emitter);
      assertThat(scanService.getEmitterCount("task-sse")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should complete all emitters for a task")
    void shouldCompleteAllEmittersForTask() {
      ScanTask task = new ScanTask("task-sse", "/data/tsfiles");
      when(scanTaskCache.getIfPresent("task-sse")).thenReturn(task);

      scanService.registerEmitter("task-sse");
      scanService.registerEmitter("task-sse");
      assertThat(scanService.getEmitterCount("task-sse")).isEqualTo(2);

      scanService.completeEmitters("task-sse");
      assertThat(scanService.getEmitterCount("task-sse")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle completeEmitters for task with no emitters")
    void shouldHandleCompleteEmittersForTaskWithNoEmitters() {
      // Should not throw
      scanService.completeEmitters("no-emitters-task");
      assertThat(scanService.getEmitterCount("no-emitters-task")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle sendEvent with no registered emitters")
    void shouldHandleSendEventWithNoRegisteredEmitters() {
      // Should not throw
      scanService.sendEvent("no-emitters-task", "progress", java.util.Map.of("test", "data"));
    }

    @Test
    @DisplayName("Should return zero emitter count for unknown task")
    void shouldReturnZeroEmitterCountForUnknownTask() {
      assertThat(scanService.getEmitterCount("unknown-task")).isEqualTo(0);
    }
  }

  // ---------------------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------------------

  private ScanTask createTaskWithResults() {
    ScanTask task = new ScanTask("task-123", "/data/tsfiles");
    task.setStartTime(LocalDateTime.now().minusMinutes(5));
    task.setEndTime(LocalDateTime.now());
    task.setStatus(ScanTaskStatus.COMPLETED);

    List<ScanResult> results = new ArrayList<>();

    // Healthy file
    results.add(
        new ScanResult("/data/tsfiles/good.tsfile", 1024L, HealthStatus.HEALTHY, List.of(), 50L));

    // Warning file
    FileError warningError =
        new FileError(
            ScanErrorType.DATA_READ_ERROR,
            "Chunk at offset 512",
            "Read error",
            ErrorSeverity.WARNING);
    results.add(
        new ScanResult(
            "/data/tsfiles/warn.tsfile", 2048L, HealthStatus.WARNING, List.of(warningError), 80L));

    // Error file
    FileError structureError =
        new FileError(
            ScanErrorType.STRUCTURE_CORRUPT,
            "File structure",
            "Corrupt structure",
            ErrorSeverity.ERROR);
    results.add(
        new ScanResult(
            "/data/tsfiles/bad.tsfile", 4096L, HealthStatus.ERROR, List.of(structureError), 120L));

    task.setResults(results);
    task.setTotalFiles(3);
    task.setScannedFiles(3);

    return task;
  }
}
