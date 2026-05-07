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
import jakarta.validation.constraints.Size;

/**
 * DTO for table-specific data query request.
 *
 * <p>Supports querying data from a specific table in a multi-table TsFile.
 */
public class TableDataRequest {

  @NotNull(message = "fileId is required")
  private String fileId;

  @NotNull(message = "tableName is required")
  private String tableName;

  private Long startTime;
  private Long endTime;
  private List<String> columns;
  private ValueRange valueRange;

  @Size(max = 10, message = "Maximum 10 advanced conditions allowed")
  private List<AdvancedCondition> advancedConditions;

  @Min(value = 1, message = "limit must be at least 1")
  @Max(value = 10000, message = "limit must not exceed 10000")
  private int limit = 100;

  @Min(value = 0, message = "offset must be non-negative")
  private int offset = 0;

  /** Default constructor for JSON deserialization. */
  public TableDataRequest() {}

  public String getFileId() {
    return fileId;
  }

  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
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

  public List<String> getColumns() {
    return columns;
  }

  public void setColumns(List<String> columns) {
    this.columns = columns;
  }

  public ValueRange getValueRange() {
    return valueRange;
  }

  public void setValueRange(ValueRange valueRange) {
    this.valueRange = valueRange;
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

  public List<AdvancedCondition> getAdvancedConditions() {
    return advancedConditions;
  }

  public void setAdvancedConditions(List<AdvancedCondition> advancedConditions) {
    this.advancedConditions = advancedConditions;
  }
}
