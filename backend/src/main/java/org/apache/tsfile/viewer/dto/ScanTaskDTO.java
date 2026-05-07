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

package org.apache.tsfile.viewer.dto;

import java.time.LocalDateTime;

/**
 * DTO representing the status of a scan task.
 *
 * <p>Mirrors the internal {@code ScanTask} model for API responses, exposing task progress and
 * metadata.
 *
 * <p>Validates: Requirement 1.2 (Unique task ID), Requirement 1.5 (Scan report contents)
 *
 * @param taskId unique task identifier (format: yyyyMMddHHmmss-random6)
 * @param targetPath directory or file path being scanned
 * @param status current task status
 * @param startTime time when the task started execution
 * @param endTime time when the task finished (null if still running)
 * @param totalFiles total number of TsFile files to scan
 * @param scannedFiles number of files scanned so far
 * @param currentFile name of the file currently being scanned (null if not running)
 * @param queuePosition position in the FIFO queue (0 means currently executing)
 */
public record ScanTaskDTO(
    String taskId,
    String targetPath,
    ScanTaskStatus status,
    LocalDateTime startTime,
    LocalDateTime endTime,
    int totalFiles,
    int scannedFiles,
    String currentFile,
    int queuePosition) {}
