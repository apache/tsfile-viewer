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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import org.apache.tsfile.viewer.dto.FileErrorDTO;
import org.apache.tsfile.viewer.dto.ScanErrorType;
import org.apache.tsfile.viewer.dto.ScanReportDTO;
import org.apache.tsfile.viewer.dto.ScanResultDTO;
import org.apache.tsfile.viewer.dto.ScanTaskDTO;
import org.apache.tsfile.viewer.dto.ScanTaskStatus;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.TaskNotFoundException;
import org.apache.tsfile.viewer.tsfile.TsFileHealthChecker;

/**
 * Core service for managing scan tasks.
 *
 * <p>Responsible for:
 *
 * <ul>
 *   <li>Generating unique task IDs (format: {@code yyyyMMddHHmmss-random6})
 *   <li>Starting directory scans (async via {@link ScanTaskExecutor})
 *   <li>Performing synchronous single-file health checks
 *   <li>Querying task status and paginated reports
 *   <li>Cancelling running or queued tasks
 *   <li>Managing SSE emitters for real-time event streaming
 * </ul>
 *
 * <p>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 3.3, 6.1, 6.2, 6.3, 7.1, 9.4
 */
@Service
public class ScanService {

  private static final Logger logger = LoggerFactory.getLogger(ScanService.class);

  private static final DateTimeFormatter TASK_ID_TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private static final String TASK_ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final int TASK_ID_RANDOM_LENGTH = 6;

  /** SSE emitter timeout: 30 minutes in milliseconds. */
  private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

  private final Cache<String, ScanTask> scanTaskCache;
  private final PathValidationService pathValidationService;
  private final TsFileHealthChecker tsFileHealthChecker;
  private final ScanTaskExecutor scanTaskExecutor;

  /** ObjectMapper for serializing SSE event data to JSON. */
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  /** Per-taskId lock objects. GC-friendly alternative to String.intern(). */
  private final ConcurrentHashMap<String, Object> taskLocks = new ConcurrentHashMap<>();

  /**
   * Map of taskId to list of active SSE emitters. Uses ConcurrentHashMap for thread-safe access and
   * CopyOnWriteArrayList for safe iteration during event broadcasting.
   */
  private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> sseEmitters =
      new ConcurrentHashMap<>();

  /**
   * Creates a new ScanService.
   *
   * @param scanTaskCache Caffeine cache for storing scan tasks
   * @param pathValidationService service for validating paths against the whitelist
   * @param tsFileHealthChecker health checker for TsFile files
   * @param scanTaskExecutor async executor for directory scan tasks
   */
  public ScanService(
      Cache<String, ScanTask> scanTaskCache,
      PathValidationService pathValidationService,
      TsFileHealthChecker tsFileHealthChecker,
      ScanTaskExecutor scanTaskExecutor) {
    this.scanTaskCache = scanTaskCache;
    this.pathValidationService = pathValidationService;
    this.tsFileHealthChecker = tsFileHealthChecker;
    this.scanTaskExecutor = scanTaskExecutor;
  }

  /**
   * Starts an asynchronous directory scan.
   *
   * <p>Validates the directory path against the whitelist, creates a new {@link ScanTask}, stores
   * it in the cache, and triggers async execution via {@link ScanTaskExecutor}.
   *
   * @param directoryPath the directory path to scan for TsFile files
   * @return the generated task ID
   * @throws AccessDeniedException if the path is not in the whitelist
   */
  public String startDirectoryScan(String directoryPath) {
    // Validate path against whitelist
    validatePathOrThrow(directoryPath);

    // Generate unique task ID (with collision check)
    String taskId = generateUniqueTaskId();

    // Create and initialize the scan task
    ScanTask task = new ScanTask(taskId, directoryPath);

    // Queue position is approximate: count currently QUEUED or RUNNING tasks
    int activeCount =
        (int)
            scanTaskCache.asMap().values().stream()
                .filter(
                    t ->
                        t.getStatus() == ScanTaskStatus.QUEUED
                            || t.getStatus() == ScanTaskStatus.RUNNING)
                .count();
    task.setQueuePosition(activeCount);

    // Store in cache
    scanTaskCache.put(taskId, task);

    logger.info("Created directory scan task {} for path: {}", taskId, directoryPath);

    // Trigger async execution
    scanTaskExecutor.executeScan(task);

    return taskId;
  }

  /**
   * Performs a synchronous health check on a single TsFile.
   *
   * <p>Validates the file path against the whitelist, then delegates to {@link
   * TsFileHealthChecker#check(Path)} for the actual health check.
   *
   * @param filePath the path of the TsFile to check
   * @return the scan result as a DTO
   * @throws AccessDeniedException if the path is not in the whitelist
   */
  public ScanResultDTO checkSingleFile(String filePath) {
    // Validate path against whitelist
    validatePathOrThrow(filePath);

    logger.info("Starting single file health check: {}", filePath);

    Path path = Paths.get(filePath);
    ScanResult result = tsFileHealthChecker.check(path);

    return toScanResultDTO(result);
  }

  /**
   * Gets the current status of a scan task.
   *
   * @param taskId the task ID to query
   * @return the task status as a DTO
   * @throws TaskNotFoundException if the task ID does not exist in the cache
   */
  public ScanTaskDTO getTaskStatus(String taskId) {
    ScanTask task = getTaskOrThrow(taskId);
    return toScanTaskDTO(task);
  }

  /**
   * Gets a paginated scan report for a completed or in-progress task.
   *
   * <p>The report includes summary statistics (total files, healthy/warning/error counts, error
   * type distribution) and a paginated list of individual file results.
   *
   * @param taskId the task ID to query
   * @param page zero-based page number
   * @param size number of results per page
   * @return the scan report as a DTO with paginated results
   * @throws TaskNotFoundException if the task ID does not exist in the cache
   */
  public ScanReportDTO getReport(String taskId, int page, int size) {
    ScanTask task = getTaskOrThrow(taskId);
    // Snapshot the results to avoid ConcurrentModificationException on a live list
    List<ScanResult> allResults = List.copyOf(task.getResults());

    // Calculate pagination based on actual results collected so far
    int resultCount = allResults.size();
    int totalPages = (resultCount == 0) ? 0 : (int) Math.ceil((double) resultCount / size);
    // Clamp page to valid range
    int safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
    int fromIndex = Math.min(safePage * size, resultCount);
    int toIndex = Math.min(fromIndex + size, resultCount);

    List<ScanResult> pageResults = allResults.subList(fromIndex, toIndex);

    // Calculate summary statistics
    HealthSummary summary = computeHealthSummary(allResults);
    long totalDurationMs = computeDurationMs(task);

    // Convert page results to DTOs
    List<ScanResultDTO> resultDTOs =
        pageResults.stream().map(this::toScanResultDTO).collect(Collectors.toList());

    return new ScanReportDTO(
        taskId,
        task.getTotalFiles(),
        summary.healthyCount(),
        summary.warningCount(),
        summary.errorCount(),
        summary.errorTypeDistribution(),
        resultDTOs,
        totalDurationMs,
        safePage,
        totalPages);
  }

  /**
   * Cancels a scan task.
   *
   * <p>Sets the task's volatile {@code cancelled} flag and updates the status to {@link
   * ScanTaskStatus#CANCELLED}. If the task is already completed, cancelled, or failed, this
   * operation is a no-op.
   *
   * @param taskId the task ID to cancel
   * @throws TaskNotFoundException if the task ID does not exist in the cache
   */
  public void cancelTask(String taskId) {
    ScanTask task = getTaskOrThrow(taskId);

    // Set the cancellation flag first so the executor loop can see it
    task.setCancelled(true);

    // Atomically transition to CANCELLED if not already in a terminal state
    boolean transitioned =
        task.transitionToTerminalState(ScanTaskStatus.CANCELLED, LocalDateTime.now());
    if (!transitioned) {
      logger.info(
          "Task {} is already in terminal state: {}, skipping cancel", taskId, task.getStatus());
      return;
    }

    scanTaskCache.put(taskId, task);
    logger.info("Cancelled scan task: {}", taskId);

    // Atomically send complete event and close emitters
    finishAndCloseEmitters(task);
  }

  // ---------------------------------------------------------------------------
  // SSE emitter management
  // ---------------------------------------------------------------------------

  /**
   * Registers a new SSE emitter for the given task.
   *
   * <p>Creates an {@link SseEmitter} with a 30-minute timeout and registers lifecycle callbacks
   * (onCompletion, onTimeout, onError) to automatically remove the emitter when the connection
   * ends. The backend scan continues unaffected when an SSE connection is dropped.
   *
   * <p>Validates: Requirements 1.3, 6.1, 6.2, 6.3, 9.4
   *
   * @param taskId the task ID to subscribe to
   * @return a new SseEmitter for the client
   * @throws TaskNotFoundException if the task ID does not exist in the cache
   */
  public SseEmitter registerEmitter(String taskId) {
    // Verify the task exists
    ScanTask task = getTaskOrThrow(taskId);

    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

    CopyOnWriteArrayList<SseEmitter> emitterList =
        sseEmitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>());
    emitterList.add(emitter);

    // Register lifecycle callbacks to clean up on disconnect
    Runnable removeCallback = () -> removeEmitter(taskId, emitter);
    emitter.onCompletion(removeCallback);
    emitter.onTimeout(removeCallback);
    emitter.onError(e -> removeCallback.run());

    logger.info(
        "Registered SSE emitter for task {}, total emitters: {}", taskId, emitterList.size());

    // Send initial state to the newly connected client
    try {
      // Send current progress — use sendEvent for consistent serialization
      int percentage =
          task.getTotalFiles() > 0
              ? (int) ((task.getScannedFiles() * 100L) / task.getTotalFiles())
              : 0;
      String progressJson =
          objectMapper.writeValueAsString(
              Map.of(
                  "scannedCount",
                  task.getScannedFiles(),
                  "totalCount",
                  task.getTotalFiles(),
                  "currentFile",
                  task.getCurrentFile() != null ? task.getCurrentFile() : "",
                  "percentage",
                  percentage));
      emitter.send(
          SseEmitter.event()
              .name("progress")
              .data(progressJson, org.springframework.http.MediaType.TEXT_PLAIN));

      // Replay cached log entries for late-connecting clients
      for (String logJson : task.getLogCache()) {
        emitter.send(
            SseEmitter.event()
                .name("log")
                .data(logJson, org.springframework.http.MediaType.TEXT_PLAIN));
      }

      // Re-read status AFTER emitter is registered and logs replayed to close the race
      // window where the task completes between initial read and emitter registration.
      ScanTaskStatus status = task.getStatus();

      // If task already finished, send complete event and close
      if (status == ScanTaskStatus.COMPLETED
          || status == ScanTaskStatus.CANCELLED
          || status == ScanTaskStatus.FAILED) {
        long durationSeconds = computeDurationMs(task) / 1000;
        HealthSummary summary = computeHealthSummary(task.getResults());
        Map<String, Object> completeData = new HashMap<>();
        completeData.put("taskId", task.getTaskId());
        completeData.put("status", task.getStatus().name());
        completeData.put("duration", durationSeconds);
        completeData.put("totalFiles", task.getTotalFiles());
        completeData.put("healthyCount", summary.healthyCount());
        completeData.put("warningCount", summary.warningCount());
        completeData.put("errorCount", summary.errorCount());
        String completeJson = objectMapper.writeValueAsString(completeData);
        emitter.send(
            SseEmitter.event()
                .name("complete")
                .data(completeJson, org.springframework.http.MediaType.TEXT_PLAIN));
        emitter.complete();
      }
    } catch (IOException e) {
      logger.warn(
          "Failed to send initial state to SSE emitter for task {}: {}", taskId, e.getMessage());
    }

    return emitter;
  }

  /**
   * Removes a specific SSE emitter for the given task.
   *
   * <p>Called automatically by the emitter lifecycle callbacks (onCompletion, onTimeout, onError).
   * If no emitters remain for the task, the task entry is removed from the map.
   *
   * @param taskId the task ID
   * @param emitter the emitter to remove
   */
  void removeEmitter(String taskId, SseEmitter emitter) {
    sseEmitters.computeIfPresent(
        taskId,
        (key, emitterList) -> {
          emitterList.remove(emitter);
          if (emitterList.isEmpty()) {
            logger.debug("Removed last SSE emitter for task {}", taskId);
            return null; // removes the entry atomically
          }
          logger.debug(
              "Removed SSE emitter for task {}, remaining emitters: {}",
              taskId,
              emitterList.size());
          return emitterList;
        });
  }

  /**
   * Serializes an event data object to JSON string. Used by ScanTaskExecutor to cache log entries.
   *
   * @param data the event data object to serialize
   * @return the JSON string representation
   * @throws IOException if serialization fails
   */
  public String serializeEvent(Object data) throws IOException {
    return objectMapper.writeValueAsString(data);
  }

  /**
   * Sends an SSE event to all registered emitters for the given task.
   *
   * <p>The event data is serialized to JSON using Jackson. If sending to a specific emitter fails
   * (e.g., client disconnected), that emitter is completed and removed. The backend scan continues
   * unaffected.
   *
   * <p>Validates: Requirements 1.3, 3.3, 6.1, 6.2, 6.3, 9.4
   *
   * @param taskId the task ID to send the event to
   * @param eventType the SSE event type (e.g., "progress", "log", "error-found", "complete",
   *     "status-change")
   * @param data the event data object to serialize as JSON
   */
  public void sendEvent(String taskId, String eventType, Object data) {
    CopyOnWriteArrayList<SseEmitter> emitterList = sseEmitters.get(taskId);
    if (emitterList == null || emitterList.isEmpty()) {
      return;
    }

    String jsonData;
    try {
      jsonData = objectMapper.writeValueAsString(data);
    } catch (IOException e) {
      logger.error("Failed to serialize SSE event data for task {}: {}", taskId, e.getMessage());
      return;
    }

    for (SseEmitter emitter : emitterList) {
      try {
        emitter.send(
            SseEmitter.event()
                .name(eventType)
                .data(jsonData, org.springframework.http.MediaType.TEXT_PLAIN));
      } catch (IOException | IllegalStateException e) {
        logger.debug(
            "Failed to send SSE event to emitter for task {}, removing: {}",
            taskId,
            e.getMessage());
        try {
          emitter.complete();
        } catch (Exception ignored) {
          // Emitter may already be completed
        }
        removeEmitter(taskId, emitter);
      }
    }
  }

  /**
   * Atomically sends the complete SSE event and closes all emitters for the given task.
   *
   * <p>This method is synchronized on the task ID to prevent race conditions between the executor
   * thread completing a task and a concurrent cancel request. Only the first caller will actually
   * send the event and close emitters.
   *
   * @param task the completed scan task
   */
  public void finishAndCloseEmitters(ScanTask task) {
    String taskId = task.getTaskId();
    // Synchronize on a per-taskId lock object (GC-friendly, unlike String.intern())
    Object lock = taskLocks.computeIfAbsent(taskId, k -> new Object());
    synchronized (lock) {
      CopyOnWriteArrayList<SseEmitter> emitterList = sseEmitters.get(taskId);
      if (emitterList == null || emitterList.isEmpty()) {
        // Already closed by another thread or no emitters registered
        sseEmitters.remove(taskId);
        return;
      }

      // Build complete event data
      long durationSeconds = computeDurationMs(task) / 1000;
      HealthSummary summary = computeHealthSummary(task.getResults());

      Map<String, Object> completeData = new HashMap<>();
      completeData.put("taskId", taskId);
      completeData.put("status", task.getStatus().name());
      completeData.put("duration", durationSeconds);
      completeData.put("totalFiles", task.getTotalFiles());
      completeData.put("healthyCount", summary.healthyCount());
      completeData.put("warningCount", summary.warningCount());
      completeData.put("errorCount", summary.errorCount());

      // Send complete event then close all emitters atomically
      sendEvent(taskId, "complete", completeData);
      completeEmitters(taskId);
    }
  }

  /**
   * Completes all SSE emitters for the given task.
   *
   * <p>Called when a scan task reaches a terminal state (COMPLETED, CANCELLED, FAILED). Sends a
   * completion signal to all connected clients and cleans up the emitter list.
   *
   * <p>Validates: Requirement 6.3
   *
   * @param taskId the task ID whose emitters should be completed
   */
  public void completeEmitters(String taskId) {
    CopyOnWriteArrayList<SseEmitter> emitterList = sseEmitters.remove(taskId);
    if (emitterList != null) {
      for (SseEmitter emitter : emitterList) {
        try {
          emitter.complete();
        } catch (Exception e) {
          logger.debug("Error completing SSE emitter for task {}: {}", taskId, e.getMessage());
        }
      }
      logger.info("Completed all SSE emitters for task {}", taskId);
    }
    taskLocks.remove(taskId);
  }

  /**
   * Returns the number of active SSE emitters for the given task. Primarily used for testing.
   *
   * @param taskId the task ID
   * @return the number of active emitters, or 0 if none
   */
  int getEmitterCount(String taskId) {
    CopyOnWriteArrayList<SseEmitter> emitterList = sseEmitters.get(taskId);
    return emitterList != null ? emitterList.size() : 0;
  }

  // ---------------------------------------------------------------------------
  // Report export
  // ---------------------------------------------------------------------------

  /**
   * Exports a scan report in the specified format.
   *
   * <p>Supported formats:
   *
   * <ul>
   *   <li>{@code json} — Full ScanReport serialized as JSON using Jackson ObjectMapper
   *   <li>{@code csv} — RFC 4180 compliant CSV with header row and data rows
   * </ul>
   *
   * <p>CSV columns: filePath, fileSize, healthStatus, errorType, severity, location, description,
   * scanDurationMs. For files with multiple errors, each error produces a separate row. For healthy
   * files (no errors), a single row is produced with empty error fields.
   *
   * <p>Validates: Requirements 4.1, 4.2, 4.3
   *
   * @param taskId the task ID to export the report for
   * @param format the export format ("json" or "csv")
   * @return the exported report as a byte array
   * @throws TaskNotFoundException if the task ID does not exist in the cache
   * @throws IllegalArgumentException if the format is not "json" or "csv"
   */
  public byte[] exportReport(String taskId, String format) {
    ScanTask task = getTaskOrThrow(taskId);

    int resultCount = task.getResults().size();
    if (resultCount > 5000) {
      logger.warn(
          "Exporting large scan report: taskId={}, resultCount={}, format={}. "
              + "This may consume significant memory.",
          taskId,
          resultCount,
          format);
    }

    return switch (format.toLowerCase()) {
      case "json" -> exportAsJson(task);
      case "csv" -> exportAsCsv(task);
      default ->
          throw new IllegalArgumentException(
              "Unsupported export format: " + format + ". Supported formats: json, csv");
    };
  }

  /**
   * Exports the scan report as JSON.
   *
   * <p>Builds a full {@link ScanReport} from the task data and serializes it using Jackson
   * ObjectMapper.
   *
   * @param task the scan task to export
   * @return JSON bytes
   */
  private byte[] exportAsJson(ScanTask task) {
    ScanReport report = buildScanReport(task);
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize scan report to JSON", e);
    }
  }

  /**
   * Exports the scan report as RFC 4180 compliant CSV.
   *
   * <p>CSV columns: filePath, fileSize, healthStatus, errorType, severity, location, description,
   * scanDurationMs.
   *
   * <p>For files with multiple errors, each error produces a separate row. For healthy files with
   * no errors, a single row is produced with empty error fields.
   *
   * <p>RFC 4180 compliance: fields containing commas, double quotes, or newlines are enclosed in
   * double quotes, and double quotes within fields are escaped by doubling them.
   *
   * @param task the scan task to export
   * @return CSV bytes (UTF-8 encoded)
   */
  private byte[] exportAsCsv(ScanTask task) {
    StringBuilder csv = new StringBuilder();

    // Header row
    csv.append(
        "filePath,fileSize,healthStatus,errorType,severity,location,description,scanDurationMs\r\n");

    // Data rows
    for (ScanResult result : task.getResults()) {
      if (result.getErrors().isEmpty()) {
        // Healthy file: single row with empty error fields
        csv.append(csvEscape(result.getFilePath()));
        csv.append(',');
        csv.append(result.getFileSize());
        csv.append(',');
        csv.append(csvEscape(String.valueOf(result.getHealthStatus())));
        csv.append(',');
        csv.append(','); // empty errorType
        csv.append(','); // empty severity
        csv.append(','); // empty location
        csv.append(','); // empty description
        csv.append(result.getScanDurationMs());
        csv.append("\r\n");
      } else {
        // File with errors: one row per error
        for (FileError error : result.getErrors()) {
          csv.append(csvEscape(result.getFilePath()));
          csv.append(',');
          csv.append(result.getFileSize());
          csv.append(',');
          csv.append(csvEscape(String.valueOf(result.getHealthStatus())));
          csv.append(',');
          csv.append(csvEscape(error.getErrorType() != null ? error.getErrorType().name() : ""));
          csv.append(',');
          csv.append(csvEscape(error.getSeverity() != null ? error.getSeverity().name() : ""));
          csv.append(',');
          csv.append(csvEscape(error.getLocation()));
          csv.append(',');
          csv.append(csvEscape(error.getDescription()));
          csv.append(',');
          csv.append(result.getScanDurationMs());
          csv.append("\r\n");
        }
      }
    }

    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Escapes a field value for RFC 4180 CSV format.
   *
   * <p>If the value contains a comma, double quote, or newline, it is enclosed in double quotes and
   * any double quotes within the value are escaped by doubling them.
   *
   * @param value the field value to escape (may be null)
   * @return the escaped field value
   */
  String csvEscape(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",")
        || value.contains("\"")
        || value.contains("\n")
        || value.contains("\r")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  /**
   * Builds a full {@link ScanReport} from a {@link ScanTask}.
   *
   * <p>Aggregates all results (not paginated) and computes summary statistics.
   *
   * @param task the scan task
   * @return the complete scan report
   */
  ScanReport buildScanReport(ScanTask task) {
    ScanReport report = new ScanReport(task.getTaskId());
    List<ScanResult> allResults = task.getResults();

    report.setResults(new java.util.ArrayList<>(allResults));
    report.setTotalFiles(task.getTotalFiles());

    HealthSummary summary = computeHealthSummary(allResults);
    report.setHealthyCount(summary.healthyCount());
    report.setWarningCount(summary.warningCount());
    report.setErrorCount(summary.errorCount());
    report.setErrorTypeDistribution(summary.errorTypeDistribution());
    report.setTotalDurationMs(computeDurationMs(task));

    return report;
  }

  // ---------------------------------------------------------------------------
  // Summary computation helper
  // ---------------------------------------------------------------------------

  /** Holds aggregated health status counts from scan results. */
  record HealthSummary(
      int healthyCount,
      int warningCount,
      int errorCount,
      Map<ScanErrorType, Integer> errorTypeDistribution) {}

  /**
   * Computes health status counts and error type distribution from a list of scan results.
   *
   * @param results the scan results to aggregate
   * @return the computed summary
   */
  private HealthSummary computeHealthSummary(List<ScanResult> results) {
    int healthyCount = 0;
    int warningCount = 0;
    int errorCount = 0;
    Map<ScanErrorType, Integer> errorTypeDistribution = new HashMap<>();
    for (ScanResult result : results) {
      switch (result.getHealthStatus()) {
        case HEALTHY -> healthyCount++;
        case WARNING -> warningCount++;
        case ERROR -> errorCount++;
      }
      for (FileError error : result.getErrors()) {
        errorTypeDistribution.merge(error.getErrorType(), 1, Integer::sum);
      }
    }
    return new HealthSummary(healthyCount, warningCount, errorCount, errorTypeDistribution);
  }

  /**
   * Computes the task duration in milliseconds. If the task is still running, uses current time.
   */
  private long computeDurationMs(ScanTask task) {
    if (task.getStartTime() != null && task.getEndTime() != null) {
      return java.time.Duration.between(task.getStartTime(), task.getEndTime()).toMillis();
    } else if (task.getStartTime() != null) {
      return java.time.Duration.between(task.getStartTime(), LocalDateTime.now()).toMillis();
    }
    return 0;
  }

  // ---------------------------------------------------------------------------
  // Task ID generation
  // ---------------------------------------------------------------------------

  /**
   * Generates a unique task ID in the format {@code yyyyMMddHHmmss-random6}.
   *
   * <p>The random suffix consists of 6 lowercase alphanumeric characters.
   *
   * @return a unique task ID
   */
  String generateTaskId() {
    String timestamp = LocalDateTime.now().format(TASK_ID_TIMESTAMP_FORMAT);
    String random = generateRandomSuffix(TASK_ID_RANDOM_LENGTH);
    return timestamp + "-" + random;
  }

  /**
   * Generates a unique task ID, retrying if a collision is detected in the cache.
   *
   * @return a unique task ID not present in the scan task cache
   */
  private String generateUniqueTaskId() {
    for (int i = 0; i < 10; i++) {
      String taskId = generateTaskId();
      if (scanTaskCache.getIfPresent(taskId) == null) {
        return taskId;
      }
      logger.warn("Task ID collision detected: {}, retrying", taskId);
    }
    // Extremely unlikely to reach here
    throw new IllegalStateException("Failed to generate unique task ID after 10 attempts");
  }

  /**
   * Generates a random string of the specified length using lowercase letters and digits.
   *
   * @param length the desired length of the random string
   * @return a random alphanumeric string
   */
  private String generateRandomSuffix(int length) {
    StringBuilder sb = new StringBuilder(length);
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < length; i++) {
      sb.append(TASK_ID_CHARS.charAt(random.nextInt(TASK_ID_CHARS.length())));
    }
    return sb.toString();
  }

  // ---------------------------------------------------------------------------
  // Validation helpers
  // ---------------------------------------------------------------------------

  /**
   * Validates the given path against the whitelist and throws if not allowed.
   *
   * @param path the path to validate
   * @throws AccessDeniedException if the path is not in the whitelist
   */
  private void validatePathOrThrow(String path) {
    if (!pathValidationService.isPathAllowed(path)) {
      throw new AccessDeniedException("Access denied: path is outside allowed directories", path);
    }
  }

  /**
   * Retrieves a scan task from the cache or throws if not found.
   *
   * @param taskId the task ID to look up
   * @return the scan task
   * @throws TaskNotFoundException if the task does not exist
   */
  private ScanTask getTaskOrThrow(String taskId) {
    ScanTask task = scanTaskCache.getIfPresent(taskId);
    if (task == null) {
      throw new TaskNotFoundException("Scan task not found: " + taskId, taskId);
    }
    return task;
  }

  // ---------------------------------------------------------------------------
  // DTO conversion helpers
  // ---------------------------------------------------------------------------

  /**
   * Converts an internal {@link ScanTask} to a {@link ScanTaskDTO}.
   *
   * @param task the internal scan task
   * @return the DTO representation
   */
  private ScanTaskDTO toScanTaskDTO(ScanTask task) {
    return new ScanTaskDTO(
        task.getTaskId(),
        task.getTargetPath(),
        task.getStatus(),
        task.getStartTime(),
        task.getEndTime(),
        task.getTotalFiles(),
        task.getScannedFiles(),
        task.getCurrentFile(),
        task.getQueuePosition());
  }

  /**
   * Converts an internal {@link ScanResult} to a {@link ScanResultDTO}.
   *
   * @param result the internal scan result
   * @return the DTO representation
   */
  private ScanResultDTO toScanResultDTO(ScanResult result) {
    List<FileErrorDTO> errorDTOs =
        result.getErrors().stream().map(this::toFileErrorDTO).collect(Collectors.toList());

    return new ScanResultDTO(
        result.getFilePath(),
        result.getFileSize(),
        result.getHealthStatus(),
        errorDTOs,
        result.getScanDurationMs());
  }

  /**
   * Converts an internal {@link FileError} to a {@link FileErrorDTO}.
   *
   * @param error the internal file error
   * @return the DTO representation
   */
  private FileErrorDTO toFileErrorDTO(FileError error) {
    return new FileErrorDTO(
        error.getErrorType(), error.getLocation(), error.getDescription(), error.getSeverity());
  }
}
