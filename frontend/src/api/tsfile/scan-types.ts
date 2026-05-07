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
 * TsFile Scan API Types
 */

export type ScanTaskStatus =
  | "CANCELLED"
  | "COMPLETED"
  | "FAILED"
  | "QUEUED"
  | "RUNNING";

export type HealthStatus = "ERROR" | "HEALTHY" | "WARNING";

export type ScanErrorType =
  | "CHUNK_STATISTICS_MISMATCH"
  | "DATA_READ_ERROR"
  | "FORMAT_INCOMPATIBLE"
  | "STRUCTURE_CORRUPT"
  | "TIMESERIES_METADATA_MISMATCH";

export type ErrorSeverity = "CRITICAL" | "ERROR" | "WARNING";

export interface ScanTask {
  taskId: string;
  targetPath: string;
  status: ScanTaskStatus;
  startTime: string;
  endTime?: string;
  totalFiles: number;
  scannedFiles: number;
  currentFile?: string;
  queuePosition: number;
}

export interface ScanResult {
  filePath: string;
  fileSize: number;
  healthStatus: HealthStatus;
  errors: FileError[];
  scanDurationMs: number;
}

export interface FileError {
  errorType: ScanErrorType;
  location: string;
  description: string;
  severity: ErrorSeverity;
}

export interface ScanReport {
  taskId: string;
  totalFiles: number;
  healthyCount: number;
  warningCount: number;
  errorCount: number;
  errorTypeDistribution: Record<ScanErrorType, number>;
  results: ScanResult[];
  totalDurationMs: number;
  currentPage: number;
  totalPages: number;
}
