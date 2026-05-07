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

import java.util.ArrayList;
import java.util.List;

import org.apache.tsfile.viewer.dto.HealthStatus;

/**
 * Internal mutable model representing the scan result of a single TsFile.
 *
 * <p>Used internally by the scan service and health checker. Converted to {@link
 * org.apache.tsfile.viewer.dto.ScanResultDTO} for API responses.
 */
public class ScanResult {

  private String filePath;
  private long fileSize;
  private HealthStatus healthStatus;
  private List<FileError> errors;
  private long scanDurationMs;

  /** Default constructor. */
  public ScanResult() {
    this.errors = new ArrayList<>();
  }

  /**
   * Creates a new scan result with all fields.
   *
   * @param filePath absolute path of the scanned file
   * @param fileSize file size in bytes
   * @param healthStatus overall health status of the file
   * @param errors list of errors detected during the scan
   * @param scanDurationMs time taken to scan the file in milliseconds
   */
  public ScanResult(
      String filePath,
      long fileSize,
      HealthStatus healthStatus,
      List<FileError> errors,
      long scanDurationMs) {
    this.filePath = filePath;
    this.fileSize = fileSize;
    this.healthStatus = healthStatus;
    this.errors = errors != null ? errors : new ArrayList<>();
    this.scanDurationMs = scanDurationMs;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public long getFileSize() {
    return fileSize;
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public HealthStatus getHealthStatus() {
    return healthStatus;
  }

  public void setHealthStatus(HealthStatus healthStatus) {
    this.healthStatus = healthStatus;
  }

  public List<FileError> getErrors() {
    return errors;
  }

  public void setErrors(List<FileError> errors) {
    this.errors = errors;
  }

  public long getScanDurationMs() {
    return scanDurationMs;
  }

  public void setScanDurationMs(long scanDurationMs) {
    this.scanDurationMs = scanDurationMs;
  }
}
