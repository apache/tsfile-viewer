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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.exception.read.ReadProcessException;
import org.apache.tsfile.exception.write.NoMeasurementException;
import org.apache.tsfile.exception.write.NoTableException;
import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.read.query.dataset.ResultSet;
import org.apache.tsfile.read.v4.ITsFileReader;
import org.apache.tsfile.read.v4.TsFileReaderBuilder;
import org.apache.tsfile.write.schema.IMeasurementSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.apache.tsfile.viewer.dto.DeviceListResponse;
import org.apache.tsfile.viewer.dto.DeviceListResponse.DeviceInfo;
import org.apache.tsfile.viewer.dto.TableDataRequest;
import org.apache.tsfile.viewer.dto.TableDataResponse;
import org.apache.tsfile.viewer.dto.TableListResponse;
import org.apache.tsfile.viewer.dto.TableListResponse.TableInfo;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;

/**
 * Service for table-level data operations.
 *
 * <p>Provides functionality for:
 *
 * <ul>
 *   <li>Listing tables in a TsFile
 *   <li>Listing devices (unique device identifiers) in a TsFile
 *   <li>Querying data from specific tables with pagination
 *   <li>Supporting multi-table and multi-device scenarios
 * </ul>
 */
@Service
public class TableService {

  private static final Logger logger = LoggerFactory.getLogger(TableService.class);

  private final FileService fileService;

  public TableService(FileService fileService) {
    this.fileService = fileService;
  }

  /**
   * Gets a list of all tables in the specified TsFile.
   *
   * @param fileId the file identifier
   * @return TableListResponse containing table information
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if access is denied
   * @throws IOException if reading fails
   */
  public TableListResponse getTableList(String fileId)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    String filePath = fileService.getFilePath(fileId);
    logger.debug("Getting table list for file: {}", filePath);

    List<TableInfo> tableInfos = new ArrayList<>();

    try (ITsFileReader reader = new TsFileReaderBuilder().file(new File(filePath)).build()) {
      List<TableSchema> schemas = reader.getAllTableSchema();

      if (schemas == null || schemas.isEmpty()) {
        logger.debug("No tables found in file: {}", filePath);
        return TableListResponse.builder().tables(tableInfos).totalCount(0).build();
      }

      for (TableSchema schema : schemas) {
        String tableName = schema.getTableName();
        List<IMeasurementSchema> columnSchemas = schema.getColumnSchemas();
        List<ColumnCategory> categories = schema.getColumnTypes();

        List<String> allColumns = new ArrayList<>();
        List<String> tagColumns = new ArrayList<>();
        List<String> fieldColumns = new ArrayList<>();

        if (columnSchemas != null) {
          for (int i = 0; i < columnSchemas.size(); i++) {
            String colName = columnSchemas.get(i).getMeasurementName();
            allColumns.add(colName);

            if (categories != null && i < categories.size()) {
              ColumnCategory category = categories.get(i);
              if (category == ColumnCategory.TAG) {
                tagColumns.add(colName);
              } else if (category == ColumnCategory.FIELD) {
                fieldColumns.add(colName);
              }
            } else {
              fieldColumns.add(colName); // Default to FIELD
            }
          }
        }

        // Count rows (this might be expensive for large tables, consider caching)
        long rowCount = countTableRows(reader, tableName, allColumns);

        TableInfo tableInfo =
            TableInfo.builder()
                .tableName(tableName)
                .columns(allColumns)
                .tagColumns(tagColumns)
                .fieldColumns(fieldColumns)
                .rowCount(rowCount)
                .build();

        tableInfos.add(tableInfo);
      }
    }

    logger.debug("Found {} tables in file: {}", tableInfos.size(), filePath);
    return TableListResponse.builder().tables(tableInfos).totalCount(tableInfos.size()).build();
  }

  /**
   * Gets a list of all unique devices in the specified TsFile.
   *
   * @param fileId the file identifier
   * @param tableName optional table name filter
   * @return DeviceListResponse containing device information
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if access is denied
   * @throws IOException if reading fails
   */
  public DeviceListResponse getDeviceList(String fileId, String tableName)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    String filePath = fileService.getFilePath(fileId);
    logger.debug("Getting device list for file: {}", filePath);

    Map<String, DeviceInfo> deviceMap = new HashMap<>();

    try (ITsFileReader reader = new TsFileReaderBuilder().file(new File(filePath)).build()) {
      List<TableSchema> schemas = reader.getAllTableSchema();

      if (schemas == null || schemas.isEmpty()) {
        return DeviceListResponse.builder().devices(new ArrayList<>()).totalCount(0).build();
      }

      for (TableSchema schema : schemas) {
        String tblName = schema.getTableName();

        // Filter by table name if provided
        if (tableName != null && !tableName.isEmpty() && !tblName.equals(tableName)) {
          continue;
        }

        List<IMeasurementSchema> columnSchemas = schema.getColumnSchemas();
        List<ColumnCategory> categories = schema.getColumnTypes();

        // Get tag column indices
        List<Integer> tagIndices = new ArrayList<>();
        List<String> tagNames = new ArrayList<>();
        List<String> allColumns = new ArrayList<>();

        if (columnSchemas != null && categories != null) {
          for (int i = 0; i < columnSchemas.size() && i < categories.size(); i++) {
            String colName = columnSchemas.get(i).getMeasurementName();
            allColumns.add(colName);
            if (categories.get(i) == ColumnCategory.TAG) {
              tagIndices.add(i);
              tagNames.add(colName);
            }
          }
        }

        if (allColumns.isEmpty()) {
          continue;
        }

        // Query to find unique device identifiers
        try (ResultSet rs = reader.query(tblName, allColumns, Long.MIN_VALUE, Long.MAX_VALUE)) {
          while (rs.next()) {
            List<String> tagValues = new ArrayList<>();
            for (Integer idx : tagIndices) {
              // ResultSet column indices: 1=timestamp, 2+=data columns
              // So column at index i in queryColumns is at ResultSet index i+2
              int colIdx = idx + 2;
              if (rs.isNull(colIdx)) {
                tagValues.add("null");
              } else {
                Object value = extractValue(rs, colIdx, columnSchemas.get(idx).getType());
                tagValues.add(String.valueOf(value));
              }
            }

            // Construct device identifier
            String deviceId =
                tagValues.isEmpty() ? tblName : tblName + "." + String.join(".", tagValues);

            // Update device info
            DeviceInfo existing = deviceMap.get(deviceId);
            if (existing == null) {
              deviceMap.put(
                  deviceId,
                  DeviceInfo.builder()
                      .deviceId(deviceId)
                      .tableName(tblName)
                      .tagValues(tagValues)
                      .dataPointCount(1)
                      .build());
            } else {
              existing.setDataPointCount(existing.getDataPointCount() + 1);
            }
          }
        } catch (NoTableException | NoMeasurementException | ReadProcessException e) {
          logger.warn("Error querying table {}: {}", tblName, e.getMessage());
        }
      }
    }

    List<DeviceInfo> devices = new ArrayList<>(deviceMap.values());
    logger.debug("Found {} unique devices", devices.size());

    return DeviceListResponse.builder().devices(devices).totalCount(devices.size()).build();
  }

  /**
   * Queries data from a specific table with pagination.
   *
   * @param request the table data request
   * @return TableDataResponse with paginated data
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if access is denied
   * @throws IOException if reading fails
   */
  public TableDataResponse queryTableData(TableDataRequest request)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    String filePath = fileService.getFilePath(request.getFileId());
    String tableName = request.getTableName();
    logger.debug("Querying table {} from file: {}", tableName, filePath);

    List<Map<String, Object>> rows = new ArrayList<>();
    List<String> columns = new ArrayList<>();
    List<String> columnTypes = new ArrayList<>();

    try (ITsFileReader reader = new TsFileReaderBuilder().file(new File(filePath)).build()) {
      var schemaOpt = reader.getTableSchemas(tableName);

      if (schemaOpt.isEmpty()) {
        return TableDataResponse.builder()
            .tableName(tableName)
            .columns(columns)
            .columnTypes(columnTypes)
            .rows(rows)
            .total(0)
            .limit(request.getLimit())
            .offset(request.getOffset())
            .hasMore(false)
            .build();
      }

      TableSchema schema = schemaOpt.get();
      List<IMeasurementSchema> columnSchemas = schema.getColumnSchemas();

      // Build column list
      columns.add("time");
      columnTypes.add("TIMESTAMP");

      List<String> queryColumns;
      if (request.getColumns() != null && !request.getColumns().isEmpty()) {
        queryColumns = request.getColumns();
        for (String col : queryColumns) {
          columns.add(col);
          // Find column type
          for (IMeasurementSchema cs : columnSchemas) {
            if (cs.getMeasurementName().equals(col)) {
              columnTypes.add(cs.getType().toString());
              break;
            }
          }
        }
      } else {
        queryColumns = new ArrayList<>();
        for (IMeasurementSchema cs : columnSchemas) {
          queryColumns.add(cs.getMeasurementName());
          columns.add(cs.getMeasurementName());
          columnTypes.add(cs.getType().toString());
        }
      }

      // Query data with time range
      long startTime = request.getStartTime() != null ? request.getStartTime() : Long.MIN_VALUE;
      long endTime = request.getEndTime() != null ? request.getEndTime() : Long.MAX_VALUE;

      int offset = request.getOffset();
      int limit = request.getLimit();
      int skipped = 0;
      int collected = 0;
      int filteredCount = 0; // Count of rows that pass all filters
      boolean hasMore = false;

      try (ResultSet rs = reader.query(tableName, queryColumns, startTime, endTime)) {
        while (rs.next()) {
          // Apply value filter if specified
          if (request.getValueRange() != null && request.getValueRange().hasBounds()) {
            boolean passFilter = true;
            for (int i = 0; i < queryColumns.size() && passFilter; i++) {
              // ResultSet column indices: 1=timestamp, 2+=data columns
              // So column at index i in queryColumns is at ResultSet index i+2
              int colIdx = i + 2;
              if (!rs.isNull(colIdx)) {
                IMeasurementSchema cs = null;
                for (IMeasurementSchema s : columnSchemas) {
                  if (s.getMeasurementName().equals(queryColumns.get(i))) {
                    cs = s;
                    break;
                  }
                }
                if (cs != null && isNumericType(cs.getType())) {
                  double value = rs.getDouble(colIdx);
                  if (request.getValueRange().getMin() != null
                      && value < request.getValueRange().getMin()) {
                    passFilter = false;
                  }
                  if (request.getValueRange().getMax() != null
                      && value > request.getValueRange().getMax()) {
                    passFilter = false;
                  }
                }
              }
            }
            if (!passFilter) {
              continue;
            }
          }

          // Apply advanced conditions filter if specified
          if (request.getAdvancedConditions() != null
              && !request.getAdvancedConditions().isEmpty()) {
            Map<String, Object> rowValues = new HashMap<>();
            for (int i = 0; i < queryColumns.size(); i++) {
              int colIdx = i + 2;
              String colName = queryColumns.get(i);
              if (!rs.isNull(colIdx)) {
                IMeasurementSchema cs = null;
                for (IMeasurementSchema s : columnSchemas) {
                  if (s.getMeasurementName().equals(colName)) {
                    cs = s;
                    break;
                  }
                }
                if (cs != null) {
                  rowValues.put(colName, extractValue(rs, colIdx, cs.getType()));
                }
              }
            }
            if (!ConditionEvaluator.matchesAdvancedConditions(
                rowValues, request.getAdvancedConditions())) {
              continue;
            }
          }

          // Row passes all filters, count it
          filteredCount++;

          if (skipped < offset) {
            skipped++;
            continue;
          }

          if (collected >= limit) {
            hasMore = true;
            continue; // Continue counting filtered total
          }

          // Collect row data
          Map<String, Object> row = new HashMap<>();
          row.put("time", rs.getLong(1));

          for (int i = 0; i < queryColumns.size(); i++) {
            // ResultSet column indices: 1=timestamp, 2+=data columns
            int colIdx = i + 2;
            String colName = queryColumns.get(i);
            if (rs.isNull(colIdx)) {
              row.put(colName, null);
            } else {
              IMeasurementSchema cs = null;
              for (IMeasurementSchema s : columnSchemas) {
                if (s.getMeasurementName().equals(colName)) {
                  cs = s;
                  break;
                }
              }
              if (cs != null) {
                row.put(colName, extractValue(rs, colIdx, cs.getType()));
              }
            }
          }

          rows.add(row);
          collected++;
        }
      } catch (NoTableException | NoMeasurementException | ReadProcessException e) {
        logger.warn("Error querying table {}: {}", tableName, e.getMessage());
      }

      return TableDataResponse.builder()
          .tableName(tableName)
          .columns(columns)
          .columnTypes(columnTypes)
          .rows(rows)
          .total(filteredCount)
          .limit(limit)
          .offset(offset)
          .hasMore(hasMore)
          .build();
    }
  }

  /**
   * Counts rows in a specific table with a cap to avoid full table scans.
   *
   * <p>Iterates up to {@code MAX_COUNT_ROWS} rows. If the cap is reached, returns the cap value as
   * an estimate (the UI should treat this as "at least N rows").
   *
   * @param reader the TsFile reader
   * @param tableName the table name
   * @param columns the columns to query
   * @return row count (capped at MAX_COUNT_ROWS)
   */
  private static final long MAX_COUNT_ROWS = 100_000;

  private long countTableRows(ITsFileReader reader, String tableName, List<String> columns) {
    if (columns.isEmpty()) {
      return 0;
    }

    long count = 0;
    try (ResultSet rs = reader.query(tableName, columns, Long.MIN_VALUE, Long.MAX_VALUE)) {
      while (rs.next()) {
        count++;
        if (count >= MAX_COUNT_ROWS) {
          break;
        }
      }
    } catch (Exception e) {
      logger.warn("Error counting rows for table {}: {}", tableName, e.getMessage());
    }
    return count;
  }

  /**
   * Checks if a data type is numeric.
   *
   * @param type the data type
   * @return true if numeric
   */
  private boolean isNumericType(TSDataType type) {
    return type == TSDataType.INT32
        || type == TSDataType.INT64
        || type == TSDataType.FLOAT
        || type == TSDataType.DOUBLE;
  }

  /**
   * Extracts a value from the result set based on data type.
   *
   * @param rs the result set
   * @param idx the column index
   * @param type the data type
   * @return the extracted value
   */
  private Object extractValue(ResultSet rs, int idx, TSDataType type) {
    try {
      return switch (type) {
        case BOOLEAN -> rs.getBoolean(idx);
        case INT32 -> rs.getInt(idx);
        case INT64, TIMESTAMP -> rs.getLong(idx);
        case FLOAT -> rs.getFloat(idx);
        case DOUBLE -> rs.getDouble(idx);
        case TEXT, STRING -> rs.getString(idx);
        case BLOB -> rs.getBinary(idx);
        case DATE -> rs.getDate(idx);
        default -> rs.getString(idx);
      };
    } catch (Exception e) {
      return null;
    }
  }

  // Condition evaluation delegated to shared ConditionEvaluator utility class.
}
