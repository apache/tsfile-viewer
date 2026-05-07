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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.dto.HealthStatus;
import org.apache.tsfile.viewer.dto.ScanTaskStatus;
import org.apache.tsfile.viewer.tsfile.TsFileHealthChecker;

import jakarta.annotation.PreDestroy;

/**
 * Async executor for directory scan tasks.
 *
 * <p>Uses Spring {@code @Async} with a dedicated thread pool to execute scan tasks in the
 * background. Recursively traverses directories for .tsfile files and runs health checks.
 *
 * <p>Notifies connected SSE clients via {@link ScanService} when:
 *
 * <ul>
 *   <li>Task status changes (status-change event)
 *   <li>Scan progress updates (progress event)
 *   <li>Log messages are generated (log event)
 *   <li>File errors are discovered (error-found event)
 *   <li>Task completes (complete event)
 * </ul>
 *
 * <p>Validates: Requirements 1.1, 1.3, 1.4, 3.1, 3.2, 3.3, 6.1, 6.2, 6.3, 7.2, 7.3
 */
@Component
public class ScanTaskExecutor {

  private static final Logger logger = LoggerFactory.getLogger(ScanTaskExecutor.class);

  private final ScanService scanService;
  private final TsFileHealthChecker tsFileHealthChecker;
  private final TsFileProperties tsFileProperties;

  /** Thread counter for naming file-health-check threads. */
  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

  /**
   * Dedicated executor for single-file health checks to avoid polluting the common ForkJoinPool.
   * Thread pool size is derived from {@code tsfile.scan.max-concurrent} configuration.
   */
  private final ExecutorService fileCheckExecutor;

  /**
   * Creates a new ScanTaskExecutor.
   *
   * <p>Uses {@code @Lazy} to break the circular dependency between ScanService and ScanTaskExecutor
   * (ScanService creates tasks and injects executor; executor calls back to ScanService for SSE
   * notifications).
   *
   * @param scanService the scan service for SSE event broadcasting
   * @param tsFileHealthChecker health checker for TsFile files
   * @param tsFileProperties configuration properties for timeout settings
   */
  public ScanTaskExecutor(
      @Lazy ScanService scanService,
      TsFileHealthChecker tsFileHealthChecker,
      TsFileProperties tsFileProperties) {
    this.scanService = scanService;
    this.tsFileHealthChecker = tsFileHealthChecker;
    this.tsFileProperties = tsFileProperties;
    int poolSize = Math.max(2, tsFileProperties.getScan().getMaxConcurrent() * 2);
    this.fileCheckExecutor =
        Executors.newFixedThreadPool(
            poolSize,
            r -> {
              Thread t = new Thread(r, "file-health-check-" + THREAD_COUNTER.incrementAndGet());
              t.setDaemon(true);
              return t;
            });
  }

  /** Shuts down the file check executor on application shutdown. */
  @PreDestroy
  void shutdown() {
    logger.info("Shutting down file health check executor");
    fileCheckExecutor.shutdownNow();
    try {
      if (!fileCheckExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        logger.warn("File health check executor did not terminate within 10 seconds");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Executes a directory scan task asynchronously.
   *
   * <p>Recursively traverses the target directory, collects all {@code .tsfile} files, and runs
   * {@link TsFileHealthChecker#check} on each file. Updates the task progress and notifies SSE
   * emitters after each file.
   *
   * @param task the scan task to execute
   */
  @Async("scanExecutorThreadPool")
  public void executeScan(ScanTask task) {
    String taskId = task.getTaskId();
    logger.info("Starting scan task {} for path: {}", taskId, task.getTargetPath());

    try {
      // Transition: QUEUED -> RUNNING (atomic, prevents race with cancelTask)
      String oldStatus = task.getStatus().name();
      if (!task.transitionToRunning(LocalDateTime.now())) {
        logger.info(
            "Task {} could not transition to RUNNING (current: {}), aborting",
            taskId,
            task.getStatus());
        return;
      }
      sendStatusChange(task, oldStatus, "RUNNING");
      sendLog(task, "INFO", "Scan started for: " + task.getTargetPath());

      // Collect all .tsfile files
      Path targetDir = Paths.get(task.getTargetPath());
      List<Path> tsFiles = collectTsFiles(targetDir);
      task.setTotalFiles(tsFiles.size());
      sendLog(task, "INFO", "Found " + tsFiles.size() + " .tsfile file(s)");
      sendProgress(task);

      if (tsFiles.isEmpty()) {
        sendLog(task, "WARN", "No .tsfile files found in: " + task.getTargetPath());
      }

      // Scan each file
      long taskTimeoutMs = tsFileProperties.getScan().getTaskTimeoutMinutes() * 60_000L;
      boolean taskTimedOut = false;
      for (int i = 0; i < tsFiles.size(); i++) {
        if (task.isCancelled()) {
          sendLog(task, "WARN", "Scan cancelled by user");
          break;
        }

        // Check task-level timeout
        if (task.getStartTime() != null
            && Duration.between(task.getStartTime(), LocalDateTime.now()).toMillis()
                > taskTimeoutMs) {
          sendLog(
              task,
              "ERROR",
              "Scan task timed out after "
                  + tsFileProperties.getScan().getTaskTimeoutMinutes()
                  + " minutes");
          taskTimedOut = true;
          break;
        }

        Path filePath = tsFiles.get(i);
        task.setCurrentFile(filePath.toString());
        sendLog(
            task,
            "INFO",
            "Scanning file [" + (i + 1) + "/" + tsFiles.size() + "]: " + filePath.getFileName());

        try {
          long fileTimeoutSeconds = tsFileProperties.getScan().getSingleFileTimeoutSeconds();
          Future<ScanResult> future =
              fileCheckExecutor.submit(() -> tsFileHealthChecker.check(filePath));
          ScanResult result;
          try {
            result = future.get(fileTimeoutSeconds, TimeUnit.SECONDS);
          } catch (TimeoutException te) {
            future.cancel(true);
            throw te;
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw ie;
          }
          task.getResults().add(result);

          // Send error-found events for every error
          if (result.getHealthStatus() != HealthStatus.HEALTHY) {
            for (FileError error : result.getErrors()) {
              sendErrorFound(
                  task,
                  filePath.toString(),
                  error.getErrorType().name(),
                  error.getSeverity().name());
            }
            String firstError =
                result.getErrors().isEmpty()
                    ? ""
                    : " — " + result.getErrors().get(0).getDescription();
            sendLog(
                task,
                "WARN",
                "File "
                    + filePath.getFileName()
                    + " — "
                    + result.getHealthStatus()
                    + " ("
                    + result.getErrors().size()
                    + " error(s))"
                    + firstError);
          } else {
            sendLog(task, "INFO", "File " + filePath.getFileName() + " — HEALTHY");
          }
        } catch (TimeoutException e) {
          logger.warn(
              "File scan timed out after {}s: {}",
              tsFileProperties.getScan().getSingleFileTimeoutSeconds(),
              filePath);
          ScanResult timeoutResult =
              new ScanResult(
                  filePath.toString(),
                  resolveFileSize(filePath),
                  org.apache.tsfile.viewer.dto.HealthStatus.ERROR,
                  List.of(
                      new FileError(
                          org.apache.tsfile.viewer.dto.ScanErrorType.DATA_READ_ERROR,
                          "File scan",
                          "Scan timed out after "
                              + tsFileProperties.getScan().getSingleFileTimeoutSeconds()
                              + " seconds",
                          org.apache.tsfile.viewer.dto.ErrorSeverity.ERROR)),
                  tsFileProperties.getScan().getSingleFileTimeoutSeconds() * 1000L);
          task.getResults().add(timeoutResult);
          sendLog(task, "WARN", "File " + filePath.getFileName() + " — scan timed out");
        } catch (Exception e) {
          logger.error("Error scanning file {}: {}", filePath, e.getMessage(), e);
          sendLog(
              task, "ERROR", "Failed to scan " + filePath.getFileName() + ": " + e.getMessage());
        }

        task.setScannedFiles(i + 1);
        sendProgress(task);
      }

      // Complete the task — use atomic transition to avoid race with cancelTask()
      task.setCurrentFile(null);
      ScanTaskStatus finalStatus;
      if (taskTimedOut) {
        finalStatus = ScanTaskStatus.FAILED;
      } else if (task.isCancelled()) {
        finalStatus = ScanTaskStatus.CANCELLED;
      } else {
        finalStatus = ScanTaskStatus.COMPLETED;
      }
      boolean transitioned = task.transitionToTerminalState(finalStatus, LocalDateTime.now());

      if (transitioned) {
        sendLog(task, "INFO", "Scan finished: " + task.getScannedFiles() + " file(s) scanned");
        sendComplete(task);
      } else {
        // Already in terminal state (e.g., cancelled by cancelTask() concurrently)
        logger.info(
            "Task {} already in terminal state {}, skipping complete", taskId, task.getStatus());
      }

    } catch (Exception e) {
      logger.error("Scan task {} failed: {}", taskId, e.getMessage(), e);
      task.setCurrentFile(null);
      boolean transitioned =
          task.transitionToTerminalState(ScanTaskStatus.FAILED, LocalDateTime.now());
      sendLog(task, "ERROR", "Scan failed: " + e.getMessage());
      if (transitioned) {
        sendComplete(task);
      }
    }
  }

  /** Recursively collects all .tsfile files from the given directory. */
  private List<Path> collectTsFiles(Path directory) {
    List<Path> result = new ArrayList<>();
    if (!Files.isDirectory(directory)) {
      // Single file mode
      if (directory.toString().endsWith(".tsfile") && Files.isRegularFile(directory)) {
        result.add(directory);
      }
      return result;
    }
    try (Stream<Path> walk = Files.walk(directory)) {
      walk.filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".tsfile"))
          .forEach(result::add);
    } catch (IOException e) {
      logger.error("Failed to traverse directory {}: {}", directory, e.getMessage());
    }
    return result;
  }

  /** Resolves the file size in bytes, returning 0 if the file cannot be read. */
  private long resolveFileSize(Path filePath) {
    try {
      return Files.size(filePath);
    } catch (IOException e) {
      return 0;
    }
  }

  // ---------------------------------------------------------------------------
  // SSE notification helpers
  // ---------------------------------------------------------------------------

  /**
   * Sends a status-change SSE event when a task transitions between states.
   *
   * <p>Validates: Requirements 3.3, 6.1
   *
   * @param task the scan task
   * @param oldStatus the previous status
   * @param newStatus the new status
   */
  void sendStatusChange(ScanTask task, String oldStatus, String newStatus) {
    scanService.sendEvent(
        task.getTaskId(),
        "status-change",
        java.util.Map.of(
            "taskId", task.getTaskId(), "oldStatus", oldStatus, "newStatus", newStatus));
  }

  /**
   * Sends a progress SSE event with the current scan progress.
   *
   * <p>Validates: Requirement 1.3
   *
   * @param task the scan task with updated progress
   */
  void sendProgress(ScanTask task) {
    int percentage =
        task.getTotalFiles() > 0
            ? (int) ((task.getScannedFiles() * 100L) / task.getTotalFiles())
            : 0;
    scanService.sendEvent(
        task.getTaskId(),
        "progress",
        java.util.Map.of(
            "scannedCount",
            task.getScannedFiles(),
            "totalCount",
            task.getTotalFiles(),
            "currentFile",
            task.getCurrentFile() != null ? task.getCurrentFile() : "",
            "percentage",
            percentage));
  }

  /**
   * Sends a log SSE event with a timestamped log message.
   *
   * <p>Validates: Requirements 6.1, 6.2
   *
   * @param task the scan task
   * @param level the log level (INFO, WARN, ERROR)
   * @param message the log message
   */
  void sendLog(ScanTask task, String level, String message) {
    java.util.Map<String, String> logData =
        java.util.Map.of(
            "timestamp", java.time.Instant.now().toString(), "level", level, "message", message);
    // Cache log entry for replay to late-connecting SSE clients
    try {
      task.addLogEntry(scanService.serializeEvent(logData));
    } catch (Exception e) {
      logger.debug("Failed to cache log entry: {}", e.getMessage());
    }
    scanService.sendEvent(task.getTaskId(), "log", logData);
  }

  /**
   * Sends an error-found SSE event when a file error is discovered during scanning.
   *
   * <p>Validates: Requirement 6.2
   *
   * @param task the scan task
   * @param filePath the path of the file with errors
   * @param errorType the type of error found
   * @param severity the severity of the error
   */
  void sendErrorFound(ScanTask task, String filePath, String errorType, String severity) {
    scanService.sendEvent(
        task.getTaskId(),
        "error-found",
        java.util.Map.of("filePath", filePath, "errorType", errorType, "severity", severity));
  }

  /**
   * Sends a complete SSE event when the scan task finishes, then completes all emitters.
   *
   * <p>Validates: Requirement 6.3
   *
   * @param task the completed scan task
   */
  void sendComplete(ScanTask task) {
    // Delegate to ScanService which atomically sends the complete event and closes emitters
    scanService.finishAndCloseEmitters(task);
  }
}
