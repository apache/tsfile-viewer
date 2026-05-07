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
 * DTO representing a table schema in V4 table model TsFile.
 *
 * <p>Contains table name and lists of TAG and FIELD columns.
 */
public class TableDTO {

  private String tableName;
  private List<MeasurementDTO> tagColumns;
  private List<MeasurementDTO> fieldColumns;
  private int totalColumns;

  public TableDTO() {}

  public TableDTO(
      String tableName,
      List<MeasurementDTO> tagColumns,
      List<MeasurementDTO> fieldColumns,
      int totalColumns) {
    this.tableName = tableName;
    this.tagColumns = tagColumns;
    this.fieldColumns = fieldColumns;
    this.totalColumns = totalColumns;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public List<MeasurementDTO> getTagColumns() {
    return tagColumns;
  }

  public void setTagColumns(List<MeasurementDTO> tagColumns) {
    this.tagColumns = tagColumns;
  }

  public List<MeasurementDTO> getFieldColumns() {
    return fieldColumns;
  }

  public void setFieldColumns(List<MeasurementDTO> fieldColumns) {
    this.fieldColumns = fieldColumns;
  }

  public int getTotalColumns() {
    return totalColumns;
  }

  public void setTotalColumns(int totalColumns) {
    this.totalColumns = totalColumns;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String tableName;
    private List<MeasurementDTO> tagColumns;
    private List<MeasurementDTO> fieldColumns;
    private int totalColumns;

    public Builder tableName(String tableName) {
      this.tableName = tableName;
      return this;
    }

    public Builder tagColumns(List<MeasurementDTO> tagColumns) {
      this.tagColumns = tagColumns;
      return this;
    }

    public Builder fieldColumns(List<MeasurementDTO> fieldColumns) {
      this.fieldColumns = fieldColumns;
      return this;
    }

    public Builder totalColumns(int totalColumns) {
      this.totalColumns = totalColumns;
      return this;
    }

    public TableDTO build() {
      return new TableDTO(tableName, tagColumns, fieldColumns, totalColumns);
    }
  }
}
