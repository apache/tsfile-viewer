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

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for starting a directory scan.
 *
 * <p>Validates: Requirement 1.1 (Directory scan initiation)
 *
 * @param directoryPath the directory path to scan for TsFile files
 */
public record DirectoryScanRequest(
    @NotBlank(message = "directoryPath is required") String directoryPath) {}
