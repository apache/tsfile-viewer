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

import org.apache.tsfile.viewer.dto.ErrorSeverity;
import org.apache.tsfile.viewer.dto.ScanErrorType;

/**
 * Internal mutable model representing a single error found during a TsFile health check.
 *
 * <p>Used internally by the scan service and health checker. Converted to {@link
 * org.apache.tsfile.viewer.dto.FileErrorDTO} for API responses.
 */
public class FileError {

  private ScanErrorType errorType;
  private String location;
  private String description;
  private ErrorSeverity severity;

  /** Default constructor. */
  public FileError() {}

  /**
   * Creates a new file error with all fields.
   *
   * @param errorType the category of the detected error
   * @param location description of where the error was found
   * @param description human-readable explanation of the error
   * @param severity severity level of the error
   */
  public FileError(
      ScanErrorType errorType, String location, String description, ErrorSeverity severity) {
    this.errorType = errorType;
    this.location = location;
    this.description = description;
    this.severity = severity;
  }

  public ScanErrorType getErrorType() {
    return errorType;
  }

  public void setErrorType(ScanErrorType errorType) {
    this.errorType = errorType;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public ErrorSeverity getSeverity() {
    return severity;
  }

  public void setSeverity(ErrorSeverity severity) {
    this.severity = severity;
  }
}
