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

import java.util.List;

/**
 * DTO representing the scan result of a single TsFile.
 *
 * <p>Contains the file path, size, health status, list of detected errors, and scan duration.
 *
 * <p>Validates: Requirement 2.5 (Scan result structure)
 *
 * @param filePath absolute path of the scanned file
 * @param fileSize file size in bytes
 * @param healthStatus overall health status of the file
 * @param errors list of errors detected during the scan
 * @param scanDurationMs time taken to scan the file in milliseconds
 */
public record ScanResultDTO(
    String filePath,
    long fileSize,
    HealthStatus healthStatus,
    List<FileErrorDTO> errors,
    long scanDurationMs) {}
