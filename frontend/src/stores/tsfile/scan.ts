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

/**
 * Scan Store - 扫描状态管理
 */
import type {
  HealthStatus,
  ScanErrorType,
  ScanReport,
  ScanResult,
  ScanTask,
} from "@/api/tsfile/scan-types";

import { computed, ref } from "vue";
import { defineStore } from "pinia";
import {
  cancelTask,
  checkSingleFile,
  connectSSE,
  disconnectSSE,
  getReport,
  startDirectoryScan,
} from "@/api/tsfile/scan";

export interface LogEntry {
  timestamp: string;
  level: "ERROR" | "INFO" | "WARN";
  message: string;
}

export interface ScanFilter {
  healthStatus?: HealthStatus;
  errorType?: ScanErrorType;
}

export const useScanStore = defineStore("tsfile-scan", () => {
  const currentTask = ref<ScanTask | null>(null);
  const scanResults = ref<ScanResult[]>([]);
  const pageResults = ref<ScanResult[]>([]);
  const scanReport = ref<ScanReport | null>(null);
  const logs = ref<LogEntry[]>([]);
  const isConnected = ref(false);
  const currentFilter = ref<ScanFilter>({});

  const displayResults = computed<ScanResult[]>(() =>
    pageResults.value.length > 0 ? pageResults.value : scanResults.value,
  );

  const filteredResults = computed<ScanResult[]>(() => {
    let results: ScanResult[] = displayResults.value;
    if (currentFilter.value.healthStatus) {
      results = results.filter(
        (r: ScanResult) => r.healthStatus === currentFilter.value.healthStatus,
      );
    }
    if (currentFilter.value.errorType) {
      results = results.filter((r: ScanResult) =>
        r.errors.some(
          (e: { errorType: ScanErrorType }) =>
            e.errorType === currentFilter.value.errorType,
        ),
      );
    }
    return results;
  });

  const healthStatusChartData = computed<
    Array<{ name: HealthStatus; value: number }>
  >(() => {
    // Prefer report summary (covers all files) over page-level scanResults
    if (scanReport.value) {
      return [
        { name: "HEALTHY", value: scanReport.value.healthyCount },
        { name: "WARNING", value: scanReport.value.warningCount },
        { name: "ERROR", value: scanReport.value.errorCount },
      ];
    }
    const counts: Record<HealthStatus, number> = {
      HEALTHY: 0,
      WARNING: 0,
      ERROR: 0,
    };
    for (const result of scanResults.value as ScanResult[])
      counts[result.healthStatus]++;
    return [
      { name: "HEALTHY", value: counts.HEALTHY },
      { name: "WARNING", value: counts.WARNING },
      { name: "ERROR", value: counts.ERROR },
    ];
  });

  const errorTypeChartData = computed<
    Array<{ name: ScanErrorType; value: number }>
  >(() => {
    // Prefer report summary (covers all files) over page-level scanResults
    if (scanReport.value?.errorTypeDistribution) {
      const dist = scanReport.value.errorTypeDistribution;
      return [
        { name: "FORMAT_INCOMPATIBLE", value: dist.FORMAT_INCOMPATIBLE ?? 0 },
        { name: "STRUCTURE_CORRUPT", value: dist.STRUCTURE_CORRUPT ?? 0 },
        { name: "CHUNK_STATISTICS_MISMATCH", value: dist.CHUNK_STATISTICS_MISMATCH ?? 0 },
        { name: "TIMESERIES_METADATA_MISMATCH", value: dist.TIMESERIES_METADATA_MISMATCH ?? 0 },
        { name: "DATA_READ_ERROR", value: dist.DATA_READ_ERROR ?? 0 },
        { name: "DATA_VALIDATION_SKIPPED", value: dist.DATA_VALIDATION_SKIPPED ?? 0 },
      ];
    }
    const counts: Record<ScanErrorType, number> = {
      FORMAT_INCOMPATIBLE: 0,
      STRUCTURE_CORRUPT: 0,
      CHUNK_STATISTICS_MISMATCH: 0,
      TIMESERIES_METADATA_MISMATCH: 0,
      DATA_READ_ERROR: 0,
      DATA_VALIDATION_SKIPPED: 0,
    };
    for (const result of scanResults.value) {
      const seen = new Set<ScanErrorType>();
      for (const error of result.errors) seen.add(error.errorType);
      for (const t of seen) counts[t as ScanErrorType]++;
    }
    return [
      { name: "FORMAT_INCOMPATIBLE", value: counts.FORMAT_INCOMPATIBLE },
      { name: "STRUCTURE_CORRUPT", value: counts.STRUCTURE_CORRUPT },
      {
        name: "CHUNK_STATISTICS_MISMATCH",
        value: counts.CHUNK_STATISTICS_MISMATCH,
      },
      {
        name: "TIMESERIES_METADATA_MISMATCH",
        value: counts.TIMESERIES_METADATA_MISMATCH,
      },
      { name: "DATA_READ_ERROR", value: counts.DATA_READ_ERROR },
      { name: "DATA_VALIDATION_SKIPPED", value: counts.DATA_VALIDATION_SKIPPED },
    ];
  });

  const isScanning = computed(() => {
    const status = currentTask.value?.status;
    return status === "QUEUED" || status === "RUNNING";
  });

  const progress = computed(() => {
    if (!currentTask.value || currentTask.value.totalFiles === 0) return 0;
    return Math.round(
      (currentTask.value.scannedFiles / currentTask.value.totalFiles) * 100,
    );
  });

  const MAX_LOG_ENTRIES = 50000;

  function addLog(level: LogEntry["level"], message: string, timestamp?: string) {
    logs.value.push({ timestamp: timestamp || new Date().toISOString(), level, message });
    if (logs.value.length > MAX_LOG_ENTRIES) {
      logs.value = logs.value.slice(-MAX_LOG_ENTRIES);
    }
  }

  function normalizeLogLevel(level: string): LogEntry["level"] {
    const upper = level.toUpperCase();
    if (upper === "ERROR") return "ERROR";
    if (upper === "WARN" || upper === "WARNING") return "WARN";
    return "INFO";
  }

  function resetState() {
    currentTask.value = null;
    scanResults.value = [];
    pageResults.value = [];
    scanReport.value = null;
    logs.value = [];
    currentFilter.value = {};
    handleDisconnectSSE();
  }

  function handleDisconnectSSE() {
    disconnectSSE();
    isConnected.value = false;
  }

  function connectToSSE(taskId: string) {
    handleDisconnectSSE();
    connectSSE(taskId, {
      onProgress(data) {
        if (currentTask.value) {
          currentTask.value = {
            ...currentTask.value,
            scannedFiles: data.scannedCount,
            totalFiles: data.totalCount,
            currentFile: data.currentFile,
          };
        }
      },
      onLog(data) {
        addLog(normalizeLogLevel(data.level), data.message, data.timestamp);
      },
      onErrorFound(data) {
        addLog(
          "ERROR",
          `Error found in ${data.filePath}: ${data.errorType} (${data.severity})`,
        );
      },
      onStatusChange(data) {
        if (currentTask.value) {
          currentTask.value = {
            ...currentTask.value,
            status: data.newStatus as ScanTask["status"],
          };
        }
        addLog(
          "INFO",
          `Task status changed: ${data.oldStatus} → ${data.newStatus}`,
        );
      },
      onComplete(data) {
        if (currentTask.value) {
          currentTask.value = {
            ...currentTask.value,
            status: (data.status as ScanTask["status"]) || "COMPLETED",
            endTime: new Date().toISOString(),
            scannedFiles: data.totalFiles,
            totalFiles: data.totalFiles,
          };
        }
        addLog(
          "INFO",
          `Scan ${data.status || "complete"}: ${data.totalFiles} files (healthy: ${data.healthyCount}, warning: ${data.warningCount}, errors: ${data.errorCount})`,
        );
        isConnected.value = false;
        // Auto-fetch the report so results are displayed
        if (data.taskId) {
          fetchReport(data.taskId, 0).catch((err) => {
            addLog("ERROR", `Failed to fetch report: ${err?.message || "unknown error"}`);
          });
        }
      },
      onConnectionError() {
        isConnected.value = false;
        addLog("ERROR", "SSE connection lost after max retries");
      },
    });
    isConnected.value = true;
  }

  async function startScan(path: string) {
    resetState();
    const response = await startDirectoryScan({ directoryPath: path });
    currentTask.value = {
      taskId: response.taskId,
      targetPath: path,
      status: "QUEUED",
      startTime: new Date().toISOString(),
      totalFiles: 0,
      scannedFiles: 0,
      queuePosition: response.queuePosition,
    };
    addLog(
      "INFO",
      `Scan started for directory: ${path} (taskId: ${response.taskId})`,
    );
    connectToSSE(response.taskId);
  }

  async function startFileScan(path: string) {
    resetState();
    addLog("INFO", `Starting single file scan: ${path}`);
    currentTask.value = {
      taskId: "single-file",
      targetPath: path,
      status: "RUNNING",
      startTime: new Date().toISOString(),
      totalFiles: 1,
      scannedFiles: 0,
      queuePosition: 0,
    };
    try {
      const result = await checkSingleFile({ filePath: path });
      scanResults.value = [result];
      currentTask.value = {
        ...currentTask.value,
        status: "COMPLETED",
        endTime: new Date().toISOString(),
        scannedFiles: 1,
      };
      addLog(
        "INFO",
        `File scan complete: ${result.healthStatus} (${result.errors.length} error(s))`,
      );
    } catch (error: any) {
      currentTask.value = {
        ...currentTask.value,
        status: "FAILED",
        endTime: new Date().toISOString(),
      };
      addLog("ERROR", `File scan failed: ${error?.message || "unknown error"}`);
      throw error;
    }
  }

  async function cancelScan() {
    if (!currentTask.value) return;
    const taskId = currentTask.value.taskId;
    // Single-file scans are synchronous and have no backend task to cancel
    if (taskId !== "single-file") {
      await cancelTask(taskId);
    }
    currentTask.value = {
      ...currentTask.value,
      status: "CANCELLED",
      endTime: new Date().toISOString(),
    };
    addLog("WARN", `Scan cancelled: ${taskId}`);
    handleDisconnectSSE();
  }

  async function fetchReport(taskId: string, page = 0) {
    const report = await getReport(taskId, page);
    scanReport.value = report;
    pageResults.value = report.results;
  }

  function setFilter(filter: ScanFilter) {
    currentFilter.value = { ...filter };
  }

  function clearFilter() {
    currentFilter.value = {};
  }

  return {
    currentTask,
    scanResults,
    scanReport,
    logs,
    isConnected,
    currentFilter,
    filteredResults,
    healthStatusChartData,
    errorTypeChartData,
    isScanning,
    progress,
    startScan,
    startFileScan,
    cancelScan,
    connectSSE: connectToSSE,
    disconnectSSE: handleDisconnectSSE,
    fetchReport,
    setFilter,
    clearFilter,
  };
});
