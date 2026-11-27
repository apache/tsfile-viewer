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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.tsfile.viewer.dto.DirectoryScanRequest;
import org.apache.tsfile.viewer.dto.ErrorSeverity;
import org.apache.tsfile.viewer.dto.FileErrorDTO;
import org.apache.tsfile.viewer.dto.FileScanRequest;
import org.apache.tsfile.viewer.dto.HealthStatus;
import org.apache.tsfile.viewer.dto.ScanErrorType;
import org.apache.tsfile.viewer.dto.ScanReportDTO;
import org.apache.tsfile.viewer.dto.ScanResultDTO;
import org.apache.tsfile.viewer.dto.ScanTaskDTO;
import org.apache.tsfile.viewer.dto.ScanTaskStatus;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.TaskNotFoundException;
import org.apache.tsfile.viewer.service.ScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Unit tests for {@link ScanController}.
 *
 * <p>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 2.1, 3.3, 6.1, 6.2, 6.3, 7.1, 8.5, 9.4
 */
class ScanControllerTest {

  private ScanController controller;
  private ScanService scanService;

  @BeforeEach
  void setUp() {
    scanService = mock(ScanService.class);
    controller = new ScanController(scanService);
  }

  @Nested
  @DisplayName("POST /api/scan/directory")
  class StartDirectoryScanTests {

    @Test
    @DisplayName("Should start scan and return taskId with queuePosition")
    void shouldStartDirectoryScanSuccessfully() {
      String taskId = "20260206142530-abc123";
      when(scanService.startDirectoryScan("/data/tsfiles")).thenReturn(taskId);
      when(scanService.getTaskStatus(taskId))
          .thenReturn(
              new ScanTaskDTO(
                  taskId,
                  "/data/tsfiles",
                  ScanTaskStatus.QUEUED,
                  LocalDateTime.now(),
                  null,
                  0,
                  0,
                  null,
                  1));

      DirectoryScanRequest request = new DirectoryScanRequest("/data/tsfiles");
      ResponseEntity<Map<String, Object>> response = controller.startDirectoryScan(request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("taskId")).isEqualTo(taskId);
      assertThat(response.getBody().get("queuePosition")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return queuePosition 0 when task starts immediately")
    void shouldReturnQueuePositionZeroWhenImmediate() {
      String taskId = "20260206142530-abc123";
      when(scanService.startDirectoryScan("/data/tsfiles")).thenReturn(taskId);
      when(scanService.getTaskStatus(taskId))
          .thenReturn(
              new ScanTaskDTO(
                  taskId,
                  "/data/tsfiles",
                  ScanTaskStatus.RUNNING,
                  LocalDateTime.now(),
                  null,
                  10,
                  0,
                  null,
                  0));

      DirectoryScanRequest request = new DirectoryScanRequest("/data/tsfiles");
      ResponseEntity<Map<String, Object>> response = controller.startDirectoryScan(request);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("queuePosition")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should propagate AccessDeniedException for unauthorized path")
    void shouldPropagateAccessDeniedForUnauthorizedPath() {
      when(scanService.startDirectoryScan("/etc/secret"))
          .thenThrow(new AccessDeniedException("Access denied: /etc/secret"));

      DirectoryScanRequest request = new DirectoryScanRequest("/etc/secret");

      assertThatThrownBy(() -> controller.startDirectoryScan(request))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Access denied");
    }
  }

  @Nested
  @DisplayName("POST /api/scan/file")
  class CheckSingleFileTests {

    @Test
    @DisplayName("Should return scan result for healthy file")
    void shouldReturnScanResultForHealthyFile() {
      ScanResultDTO result =
          new ScanResultDTO(
              "/data/tsfiles/test.tsfile", 1024L, HealthStatus.HEALTHY, List.of(), 50L);
      when(scanService.checkSingleFile("/data/tsfiles/test.tsfile")).thenReturn(result);

      FileScanRequest request = new FileScanRequest("/data/tsfiles/test.tsfile");
      ResponseEntity<ScanResultDTO> response = controller.checkSingleFile(request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().filePath()).isEqualTo("/data/tsfiles/test.tsfile");
      assertThat(response.getBody().fileSize()).isEqualTo(1024L);
      assertThat(response.getBody().healthStatus()).isEqualTo(HealthStatus.HEALTHY);
      assertThat(response.getBody().errors()).isEmpty();
      assertThat(response.getBody().scanDurationMs()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should return scan result with errors for unhealthy file")
    void shouldReturnScanResultWithErrors() {
      FileErrorDTO error =
          new FileErrorDTO(
              ScanErrorType.STRUCTURE_CORRUPT,
              "Chunk #5 at offset 1024",
              "Structure check failed",
              ErrorSeverity.ERROR);
      ScanResultDTO result =
          new ScanResultDTO(
              "/data/tsfiles/bad.tsfile", 2048L, HealthStatus.ERROR, List.of(error), 100L);
      when(scanService.checkSingleFile("/data/tsfiles/bad.tsfile")).thenReturn(result);

      FileScanRequest request = new FileScanRequest("/data/tsfiles/bad.tsfile");
      ResponseEntity<ScanResultDTO> response = controller.checkSingleFile(request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().healthStatus()).isEqualTo(HealthStatus.ERROR);
      assertThat(response.getBody().errors()).hasSize(1);
      assertThat(response.getBody().errors().get(0).errorType())
          .isEqualTo(ScanErrorType.STRUCTURE_CORRUPT);
      assertThat(response.getBody().errors().get(0).severity()).isEqualTo(ErrorSeverity.ERROR);
    }

    @Test
    @DisplayName("Should propagate AccessDeniedException for unauthorized file path")
    void shouldPropagateAccessDeniedForUnauthorizedPath() {
      when(scanService.checkSingleFile("/etc/secret.tsfile"))
          .thenThrow(new AccessDeniedException("Access denied"));

      FileScanRequest request = new FileScanRequest("/etc/secret.tsfile");

      assertThatThrownBy(() -> controller.checkSingleFile(request))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  @Nested
  @DisplayName("GET /api/scan/status/{taskId}")
  class GetTaskStatusTests {

    @Test
    @DisplayName("Should return task status for existing task")
    void shouldReturnTaskStatus() {
      ScanTaskDTO taskDTO =
          new ScanTaskDTO(
              "20260206142530-abc123",
              "/data/tsfiles",
              ScanTaskStatus.RUNNING,
              LocalDateTime.of(2026, 2, 6, 14, 25, 30),
              null,
              10,
              3,
              "data03.tsfile",
              0);
      when(scanService.getTaskStatus("20260206142530-abc123")).thenReturn(taskDTO);

      ResponseEntity<ScanTaskDTO> response = controller.getTaskStatus("20260206142530-abc123");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().taskId()).isEqualTo("20260206142530-abc123");
      assertThat(response.getBody().targetPath()).isEqualTo("/data/tsfiles");
      assertThat(response.getBody().status()).isEqualTo(ScanTaskStatus.RUNNING);
      assertThat(response.getBody().totalFiles()).isEqualTo(10);
      assertThat(response.getBody().scannedFiles()).isEqualTo(3);
      assertThat(response.getBody().currentFile()).isEqualTo("data03.tsfile");
      assertThat(response.getBody().queuePosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should propagate TaskNotFoundException for non-existent task")
    void shouldPropagateTaskNotFoundForNonExistentTask() {
      when(scanService.getTaskStatus("nonexistent"))
          .thenThrow(new TaskNotFoundException("Scan task not found", "nonexistent"));

      assertThatThrownBy(() -> controller.getTaskStatus("nonexistent"))
          .isInstanceOf(TaskNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("GET /api/scan/report/{taskId}")
  class GetReportTests {

    @Test
    @DisplayName("Should return paginated report with default parameters")
    void shouldReturnPaginatedReportWithDefaults() {
      ScanReportDTO report =
          new ScanReportDTO(
              "task-123",
              3,
              1,
              1,
              1,
              Map.of(ScanErrorType.STRUCTURE_CORRUPT, 1, ScanErrorType.DATA_READ_ERROR, 1),
              List.of(
                  new ScanResultDTO(
                      "/data/good.tsfile", 1024L, HealthStatus.HEALTHY, List.of(), 50L)),
              5000L,
              0,
              1);
      when(scanService.getReport("task-123", 0, 50)).thenReturn(report);

      ResponseEntity<ScanReportDTO> response = controller.getReport("task-123", 0, 50);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().taskId()).isEqualTo("task-123");
      assertThat(response.getBody().totalFiles()).isEqualTo(3);
      assertThat(response.getBody().healthyCount()).isEqualTo(1);
      assertThat(response.getBody().warningCount()).isEqualTo(1);
      assertThat(response.getBody().errorCount()).isEqualTo(1);
      assertThat(response.getBody().currentPage()).isEqualTo(0);
      assertThat(response.getBody().totalPages()).isEqualTo(1);

      verify(scanService).getReport("task-123", 0, 50);
    }

    @Test
    @DisplayName("Should pass custom page and size parameters")
    void shouldPassCustomPageAndSizeParameters() {
      ScanReportDTO report =
          new ScanReportDTO("task-123", 100, 80, 10, 10, Map.of(), List.of(), 10000L, 2, 4);
      when(scanService.getReport("task-123", 2, 25)).thenReturn(report);

      ResponseEntity<ScanReportDTO> response = controller.getReport("task-123", 2, 25);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().currentPage()).isEqualTo(2);
      assertThat(response.getBody().totalPages()).isEqualTo(4);

      verify(scanService).getReport("task-123", 2, 25);
    }

    @Test
    @DisplayName("Should propagate TaskNotFoundException for non-existent task")
    void shouldPropagateTaskNotFoundForNonExistentTask() {
      when(scanService.getReport("nonexistent", 0, 50))
          .thenThrow(new TaskNotFoundException("Scan task not found", "nonexistent"));

      assertThatThrownBy(() -> controller.getReport("nonexistent", 0, 50))
          .isInstanceOf(TaskNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("DELETE /api/scan/{taskId}")
  class CancelTaskTests {

    @Test
    @DisplayName("Should cancel task and return success message")
    void shouldCancelTaskSuccessfully() {
      doNothing().when(scanService).cancelTask("task-123");

      ResponseEntity<Map<String, String>> response = controller.cancelTask("task-123");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("message")).isEqualTo("Scan task cancelled: task-123");

      verify(scanService).cancelTask("task-123");
    }

    @Test
    @DisplayName("Should propagate TaskNotFoundException for non-existent task")
    void shouldPropagateTaskNotFoundForNonExistentTask() {
      doThrow(new TaskNotFoundException("Scan task not found", "nonexistent"))
          .when(scanService)
          .cancelTask("nonexistent");

      assertThatThrownBy(() -> controller.cancelTask("nonexistent"))
          .isInstanceOf(TaskNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("GET /api/scan/export/{taskId}")
  class ExportReportTests {

    @Test
    @DisplayName("Should export JSON report with correct headers")
    void shouldExportJsonReportWithCorrectHeaders() {
      byte[] jsonData = "{\"taskId\":\"task-123\"}".getBytes();
      when(scanService.exportReport("task-123", "json")).thenReturn(jsonData);

      ResponseEntity<byte[]> response = controller.exportReport("task-123", "json");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isEqualTo(jsonData);
      assertThat(response.getHeaders().getFirst("Content-Disposition"))
          .isEqualTo("attachment; filename=\"scan-report-task-123.json\"");
      assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    @DisplayName("Should export CSV report with correct headers")
    void shouldExportCsvReportWithCorrectHeaders() {
      byte[] csvData = "filePath,fileSize\r\n/data/test.tsfile,1024\r\n".getBytes();
      when(scanService.exportReport("task-123", "csv")).thenReturn(csvData);

      ResponseEntity<byte[]> response = controller.exportReport("task-123", "csv");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isEqualTo(csvData);
      assertThat(response.getHeaders().getFirst("Content-Disposition"))
          .isEqualTo("attachment; filename=\"scan-report-task-123.csv\"");
      assertThat(response.getHeaders().getFirst("Content-Type"))
          .isEqualTo("text/csv; charset=UTF-8");
    }

    @Test
    @DisplayName("Should default to JSON format when no format specified")
    void shouldDefaultToJsonFormat() {
      byte[] jsonData = "{}".getBytes();
      when(scanService.exportReport("task-123", "json")).thenReturn(jsonData);

      ResponseEntity<byte[]> response = controller.exportReport("task-123", "json");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getFirst("Content-Disposition"))
          .contains("scan-report-task-123.json\"");
    }

    @Test
    @DisplayName("Should propagate TaskNotFoundException for non-existent task")
    void shouldPropagateTaskNotFoundForNonExistentTask() {
      when(scanService.exportReport("nonexistent", "json"))
          .thenThrow(new TaskNotFoundException("Scan task not found", "nonexistent"));

      assertThatThrownBy(() -> controller.exportReport("nonexistent", "json"))
          .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    @DisplayName("Should propagate IllegalArgumentException for unsupported format")
    void shouldPropagateIllegalArgumentForUnsupportedFormat() {
      when(scanService.exportReport("task-123", "xml"))
          .thenThrow(new IllegalArgumentException("Unsupported export format: xml"));

      assertThatThrownBy(() -> controller.exportReport("task-123", "xml"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("GET /api/scan/stream/{taskId}")
  class StreamEventsTests {

    @Test
    @DisplayName("Should return SseEmitter for existing task")
    void shouldReturnSseEmitterForExistingTask() {
      SseEmitter expectedEmitter = new SseEmitter(1800000L);
      when(scanService.registerEmitter("task-123")).thenReturn(expectedEmitter);

      SseEmitter result = controller.streamEvents("task-123");

      assertThat(result).isSameAs(expectedEmitter);
      verify(scanService).registerEmitter("task-123");
    }

    @Test
    @DisplayName("Should propagate TaskNotFoundException for non-existent task")
    void shouldPropagateTaskNotFoundForNonExistentTask() {
      when(scanService.registerEmitter("nonexistent"))
          .thenThrow(new TaskNotFoundException("Scan task not found", "nonexistent"));

      assertThatThrownBy(() -> controller.streamEvents("nonexistent"))
          .isInstanceOf(TaskNotFoundException.class);
    }
  }
}
