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

/**
 * DTO representing a single error found during a TsFile health check.
 *
 * <p>Contains the error type, location within the file, human-readable description, and severity
 * level.
 *
 * <p>Validates: Requirement 2.5 (Scan result includes error list)
 *
 * @param errorType the category of the detected error
 * @param location description of where the error was found (e.g. "Chunk #5 at offset 1024")
 * @param description human-readable explanation of the error
 * @param severity severity level of the error
 */
public record FileErrorDTO(
    ScanErrorType errorType, String location, String description, ErrorSeverity severity) {}
