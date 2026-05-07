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
 * DTO for table list response.
 *
 * <p>Returns a list of tables/devices available in a TsFile with their column information.
 */
public class TableListResponse {

  private List<TableInfo> tables;
  private int totalCount;

  /** Default constructor for JSON deserialization. */
  public TableListResponse() {}

  /**
   * Creates a new table list response.
   *
   * @param tables list of table information
   * @param totalCount total number of tables
   */
  public TableListResponse(List<TableInfo> tables, int totalCount) {
    this.tables = tables;
    this.totalCount = totalCount;
  }

  public List<TableInfo> getTables() {
    return tables;
  }

  public void setTables(List<TableInfo> tables) {
    this.tables = tables;
  }

  public int getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(int totalCount) {
    this.totalCount = totalCount;
  }

  /** Information about a single table. */
  public static class TableInfo {
    private String tableName;
    private List<String> columns;
    private List<String> tagColumns;
    private List<String> fieldColumns;
    private long rowCount;

    public TableInfo() {}

    public TableInfo(
        String tableName,
        List<String> columns,
        List<String> tagColumns,
        List<String> fieldColumns,
        long rowCount) {
      this.tableName = tableName;
      this.columns = columns;
      this.tagColumns = tagColumns;
      this.fieldColumns = fieldColumns;
      this.rowCount = rowCount;
    }

    public String getTableName() {
      return tableName;
    }

    public void setTableName(String tableName) {
      this.tableName = tableName;
    }

    public List<String> getColumns() {
      return columns;
    }

    public void setColumns(List<String> columns) {
      this.columns = columns;
    }

    public List<String> getTagColumns() {
      return tagColumns;
    }

    public void setTagColumns(List<String> tagColumns) {
      this.tagColumns = tagColumns;
    }

    public List<String> getFieldColumns() {
      return fieldColumns;
    }

    public void setFieldColumns(List<String> fieldColumns) {
      this.fieldColumns = fieldColumns;
    }

    public long getRowCount() {
      return rowCount;
    }

    public void setRowCount(long rowCount) {
      this.rowCount = rowCount;
    }

    public static Builder builder() {
      return new Builder();
    }

    /** Builder for TableInfo. */
    public static class Builder {
      private String tableName;
      private List<String> columns;
      private List<String> tagColumns;
      private List<String> fieldColumns;
      private long rowCount;

      public Builder tableName(String tableName) {
        this.tableName = tableName;
        return this;
      }

      public Builder columns(List<String> columns) {
        this.columns = columns;
        return this;
      }

      public Builder tagColumns(List<String> tagColumns) {
        this.tagColumns = tagColumns;
        return this;
      }

      public Builder fieldColumns(List<String> fieldColumns) {
        this.fieldColumns = fieldColumns;
        return this;
      }

      public Builder rowCount(long rowCount) {
        this.rowCount = rowCount;
        return this;
      }

      public TableInfo build() {
        return new TableInfo(tableName, columns, tagColumns, fieldColumns, rowCount);
      }
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for TableListResponse. */
  public static class Builder {
    private List<TableInfo> tables;
    private int totalCount;

    public Builder tables(List<TableInfo> tables) {
      this.tables = tables;
      return this;
    }

    public Builder totalCount(int totalCount) {
      this.totalCount = totalCount;
      return this;
    }

    public TableListResponse build() {
      return new TableListResponse(tables, totalCount);
    }
  }
}
