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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for data preview request.
 *
 * <p>Validates: Requirement 3 (Data preview)
 */
public class DataPreviewRequest {

  @NotNull(message = "fileId is required")
  private String fileId;

  private Long startTime;
  private Long endTime;
  private List<String> devices;
  private List<String> measurements;
  private ValueRange valueRange;
  private List<AdvancedCondition> advancedConditions;

  @Min(value = 1, message = "limit must be at least 1")
  @Max(value = 1000, message = "limit must not exceed 1000")
  private int limit = 100;

  @Min(value = 0, message = "offset must be non-negative")
  private int offset = 0;

  /** Default constructor for JSON deserialization. */
  public DataPreviewRequest() {}

  public String getFileId() {
    return fileId;
  }

  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  public Long getStartTime() {
    return startTime;
  }

  public void setStartTime(Long startTime) {
    this.startTime = startTime;
  }

  public Long getEndTime() {
    return endTime;
  }

  public void setEndTime(Long endTime) {
    this.endTime = endTime;
  }

  public List<String> getDevices() {
    return devices;
  }

  public void setDevices(List<String> devices) {
    this.devices = devices;
  }

  public List<String> getMeasurements() {
    return measurements;
  }

  public void setMeasurements(List<String> measurements) {
    this.measurements = measurements;
  }

  public ValueRange getValueRange() {
    return valueRange;
  }

  public void setValueRange(ValueRange valueRange) {
    this.valueRange = valueRange;
  }

  public List<AdvancedCondition> getAdvancedConditions() {
    return advancedConditions;
  }

  public void setAdvancedConditions(List<AdvancedCondition> advancedConditions) {
    this.advancedConditions = advancedConditions;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

  public int getOffset() {
    return offset;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }

  /**
   * Converts this request to FilterConditions.
   *
   * @return FilterConditions based on this request
   */
  public FilterConditions toFilterConditions() {
    FilterConditions.Builder builder = FilterConditions.builder().limit(limit).offset(offset);

    if (startTime != null || endTime != null) {
      builder.timeRange(
          new TimeRange(
              startTime != null ? startTime : 0, endTime != null ? endTime : Long.MAX_VALUE));
    }

    if (devices != null && !devices.isEmpty()) {
      builder.devices(devices);
    }

    if (measurements != null && !measurements.isEmpty()) {
      builder.measurements(measurements);
    }

    if (valueRange != null) {
      builder.valueRange(valueRange);
    }

    if (advancedConditions != null && !advancedConditions.isEmpty()) {
      builder.advancedConditions(advancedConditions);
    }

    return builder.build();
  }
}
