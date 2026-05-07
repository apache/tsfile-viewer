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

package org.apache.tsfile.viewer.tsfile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.exception.read.ReadProcessException;
import org.apache.tsfile.exception.write.NoMeasurementException;
import org.apache.tsfile.exception.write.NoTableException;
import org.apache.tsfile.file.metadata.ChunkMetadata;
import org.apache.tsfile.file.metadata.IDeviceID;
import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.read.TsFileReader;
import org.apache.tsfile.read.TsFileSequenceReader;
import org.apache.tsfile.read.common.Path;
import org.apache.tsfile.read.common.RowRecord;
import org.apache.tsfile.read.expression.IExpression;
import org.apache.tsfile.read.expression.QueryExpression;
import org.apache.tsfile.read.expression.impl.BinaryExpression;
import org.apache.tsfile.read.expression.impl.GlobalTimeExpression;
import org.apache.tsfile.read.filter.factory.TimeFilterApi;
import org.apache.tsfile.read.query.dataset.QueryDataSet;
import org.apache.tsfile.read.query.dataset.ResultSet;
import org.apache.tsfile.read.v4.ITsFileReader;
import org.apache.tsfile.read.v4.TsFileReaderBuilder;
import org.apache.tsfile.write.schema.IMeasurementSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class for reading data from TsFile files.
 *
 * <p>Validates: Requirement 3.1, 3.2, 5.4 (Data reading, chunk-level access)
 */
@Component
public class TsFileDataReader {

  private static final Logger logger = LoggerFactory.getLogger(TsFileDataReader.class);

  /** Result class containing a single data row. */
  public static class DataRow {
    private final long timestamp;
    private final String tableName;
    private final Map<String, Object> values;

    public DataRow(long timestamp, String tableName, Map<String, Object> values) {
      this.timestamp = timestamp;
      this.tableName = tableName;
      this.values = values;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public String getTableName() {
      return tableName;
    }

    public Map<String, Object> getValues() {
      return values;
    }
  }

  /** Result class containing paginated data read results. */
  public static class DataReadResult {
    private final List<DataRow> data;
    private final int totalRowsRead;
    private final boolean hasMore;
    private final List<String> columnNames;
    private final List<String> columnTypes;
    private final List<String> warnings;

    public DataReadResult(
        List<DataRow> data,
        int totalRowsRead,
        boolean hasMore,
        List<String> columnNames,
        List<String> columnTypes) {
      this(data, totalRowsRead, hasMore, columnNames, columnTypes, new ArrayList<>());
    }

    public DataReadResult(
        List<DataRow> data,
        int totalRowsRead,
        boolean hasMore,
        List<String> columnNames,
        List<String> columnTypes,
        List<String> warnings) {
      this.data = data;
      this.totalRowsRead = totalRowsRead;
      this.hasMore = hasMore;
      this.columnNames = columnNames;
      this.columnTypes = columnTypes;
      this.warnings = warnings != null ? warnings : new ArrayList<>();
    }

    public List<DataRow> getData() {
      return data;
    }

    public int getTotalRowsRead() {
      return totalRowsRead;
    }

    public boolean hasMore() {
      return hasMore;
    }

    public List<String> getColumnNames() {
      return columnNames;
    }

    public List<String> getColumnTypes() {
      return columnTypes;
    }

    public List<String> getWarnings() {
      return warnings;
    }
  }

  /** Filter conditions for data reading. */
  public static class ReadFilter {
    private Long startTime;
    private Long endTime;
    private List<String> tables;
    private List<String> columns;
    private Double minValue;
    private Double maxValue;

    public ReadFilter() {}

    public Long getStartTime() {
      return startTime;
    }

    public ReadFilter setStartTime(Long startTime) {
      this.startTime = startTime;
      return this;
    }

    public Long getEndTime() {
      return endTime;
    }

    public ReadFilter setEndTime(Long endTime) {
      this.endTime = endTime;
      return this;
    }

    public List<String> getTables() {
      return tables;
    }

    public ReadFilter setTables(List<String> tables) {
      this.tables = tables;
      return this;
    }

    public List<String> getColumns() {
      return columns;
    }

    public ReadFilter setColumns(List<String> columns) {
      this.columns = columns;
      return this;
    }

    public Double getMinValue() {
      return minValue;
    }

    public ReadFilter setMinValue(Double minValue) {
      this.minValue = minValue;
      return this;
    }

    public Double getMaxValue() {
      return maxValue;
    }

    public ReadFilter setMaxValue(Double maxValue) {
      this.maxValue = maxValue;
      return this;
    }
  }

  /** Reads data from a TsFile with time range filtering and pagination. */
  public DataReadResult readDataByTimeRange(
      File filePath, Long startTime, Long endTime, int limit, int offset) throws IOException {
    long effStart = startTime != null ? startTime : Long.MIN_VALUE;
    long effEnd = endTime != null ? endTime : Long.MAX_VALUE;
    List<DataRow> results = new ArrayList<>();
    List<String> colNames = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    int skipped = 0, collected = 0;
    boolean hasMore = false;

    try (ITsFileReader reader = new TsFileReaderBuilder().file(filePath).build()) {
      List<TableSchema> schemas = new ArrayList<>();
      for (TableSchema ts : reader.getAllTableSchema()) {
        schemas.add(ts);
      }

      // If no TableSchema found, this is a tree model file - use TsFileSequenceReader
      if (schemas.isEmpty()) {
        return readTreeModelData(filePath, effStart, effEnd, limit, offset);
      }

      for (TableSchema ts : schemas) {
        if (collected >= limit) {
          hasMore = true;
          break;
        }
        String tblName = ts.getTableName();
        List<IMeasurementSchema> cols = ts.getColumnSchemas();
        List<ColumnCategory> categories = ts.getColumnTypes();

        // Identify TAG columns for device identifier construction
        List<Integer> tagIndices = new ArrayList<>();
        List<String> tagNames = new ArrayList<>();
        List<String> qCols = new ArrayList<>();

        for (int i = 0; i < cols.size(); i++) {
          String colName = cols.get(i).getMeasurementName();
          qCols.add(colName);
          if (categories != null
              && i < categories.size()
              && categories.get(i) == ColumnCategory.TAG) {
            tagIndices.add(i);
            tagNames.add(colName);
          }
        }

        if (qCols.isEmpty()) continue;
        if (colNames.isEmpty()) {
          colNames.add("Time");
          colTypes.add("TIMESTAMP");
          colNames.add("Device");
          colTypes.add("STRING");
          for (IMeasurementSchema c : cols) {
            colNames.add(c.getMeasurementName());
            colTypes.add(c.getType().toString());
          }
        }
        try (ResultSet rs = reader.query(tblName, qCols, effStart, effEnd)) {
          while (rs.next()) {
            if (skipped < offset) {
              skipped++;
              continue;
            }
            if (collected >= limit) {
              hasMore = true;
              break;
            }
            long ts2 = rs.getLong(1);
            Map<String, Object> vals = new HashMap<>();
            List<String> tagValues = new ArrayList<>();

            for (int i = 0; i < qCols.size(); i++) {
              int idx = i + 2;
              if (rs.isNull(idx)) {
                vals.put(qCols.get(i), null);
                if (tagIndices.contains(i)) {
                  tagValues.add("null");
                }
                continue;
              }
              Object value = extractValue(rs, idx, cols.get(i).getType());
              vals.put(qCols.get(i), value);
              if (tagIndices.contains(i)) {
                tagValues.add(String.valueOf(value));
              }
            }

            // Construct device identifier: tablename.tagvalue1.tagvalue2...
            String deviceId;
            if (tagValues.isEmpty()) {
              // No TAG columns - tree model style, use table name as device
              deviceId = tblName;
            } else {
              // Table model - construct device ID from table name and TAG values
              deviceId = tblName + "." + String.join(".", tagValues);
            }

            results.add(new DataRow(ts2, deviceId, vals));
            collected++;
          }
        } catch (NoTableException | NoMeasurementException | ReadProcessException e) {
          logger.warn("Error querying {}: {}", tblName, e.getMessage());
          warnings.add("Error querying table '" + tblName + "': " + e.getMessage());
        } catch (java.nio.BufferUnderflowException | java.nio.BufferOverflowException e) {
          logger.warn(
              "Buffer error reading table {}, possibly incompatible file version: {}",
              tblName,
              e.getMessage());
          warnings.add(
              "Failed to decode data in table '"
                  + tblName
                  + "': file may have been created with an incompatible TsFile SDK version");
        } catch (RuntimeException e) {
          logger.warn("Unexpected error reading table {}: {}", tblName, e.getMessage());
          warnings.add("Unexpected error reading table '" + tblName + "': " + e.getMessage());
        }
      }
    }
    return new DataReadResult(results, collected, hasMore, colNames, colTypes, warnings);
  }

  /** Reads data using an existing reader with time range filtering and pagination. */
  public DataReadResult readDataByTimeRange(
      ITsFileReader reader, Long startTime, Long endTime, int limit, int offset)
      throws IOException {
    long effStart = startTime != null ? startTime : Long.MIN_VALUE;
    long effEnd = endTime != null ? endTime : Long.MAX_VALUE;
    List<DataRow> results = new ArrayList<>();
    List<String> colNames = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    int skipped = 0, collected = 0;
    boolean hasMore = false;

    for (TableSchema ts : reader.getAllTableSchema()) {
      if (collected >= limit) {
        hasMore = true;
        break;
      }
      String tblName = ts.getTableName();
      List<IMeasurementSchema> cols = ts.getColumnSchemas();
      List<ColumnCategory> categories = ts.getColumnTypes();

      // Identify TAG columns for device identifier construction
      List<Integer> tagIndices = new ArrayList<>();
      List<String> qCols = new ArrayList<>();

      for (int i = 0; i < cols.size(); i++) {
        qCols.add(cols.get(i).getMeasurementName());
        if (categories != null
            && i < categories.size()
            && categories.get(i) == ColumnCategory.TAG) {
          tagIndices.add(i);
        }
      }

      if (qCols.isEmpty()) continue;
      if (colNames.isEmpty()) {
        colNames.add("Time");
        colTypes.add("TIMESTAMP");
        colNames.add("Device");
        colTypes.add("STRING");
        for (IMeasurementSchema c : cols) {
          colNames.add(c.getMeasurementName());
          colTypes.add(c.getType().toString());
        }
      }
      try (ResultSet rs = reader.query(tblName, qCols, effStart, effEnd)) {
        while (rs.next()) {
          if (skipped < offset) {
            skipped++;
            continue;
          }
          if (collected >= limit) {
            hasMore = true;
            break;
          }
          long ts2 = rs.getLong(1);
          Map<String, Object> vals = new HashMap<>();
          List<String> tagValues = new ArrayList<>();

          for (int i = 0; i < qCols.size(); i++) {
            int idx = i + 2;
            if (rs.isNull(idx)) {
              vals.put(qCols.get(i), null);
              if (tagIndices.contains(i)) {
                tagValues.add("null");
              }
              continue;
            }
            Object value = extractValue(rs, idx, cols.get(i).getType());
            vals.put(qCols.get(i), value);
            if (tagIndices.contains(i)) {
              tagValues.add(String.valueOf(value));
            }
          }

          // Construct device identifier
          String deviceId =
              tagValues.isEmpty() ? tblName : tblName + "." + String.join(".", tagValues);
          results.add(new DataRow(ts2, deviceId, vals));
          collected++;
        }
      } catch (NoTableException | NoMeasurementException | ReadProcessException e) {
        logger.warn("Error querying {}: {}", tblName, e.getMessage());
      } catch (java.nio.BufferUnderflowException | java.nio.BufferOverflowException e) {
        logger.warn(
            "Buffer error reading table {}, possibly incompatible file version: {}",
            tblName,
            e.getMessage());
      } catch (RuntimeException e) {
        logger.warn("Unexpected error reading table {}: {}", tblName, e.getMessage());
      }
    }
    return new DataReadResult(results, collected, hasMore, colNames, colTypes);
  }

  /** Reads data with comprehensive filtering and pagination. */
  public DataReadResult readDataWithFilter(File filePath, ReadFilter filter, int limit, int offset)
      throws IOException {
    Long st = filter != null ? filter.getStartTime() : null;
    Long et = filter != null ? filter.getEndTime() : null;
    List<String> fTbls = filter != null ? filter.getTables() : null;
    List<String> fCols = filter != null ? filter.getColumns() : null;
    Double minV = filter != null ? filter.getMinValue() : null;
    Double maxV = filter != null ? filter.getMaxValue() : null;
    long effStart = st != null ? st : Long.MIN_VALUE;
    long effEnd = et != null ? et : Long.MAX_VALUE;
    List<DataRow> results = new ArrayList<>();
    List<String> colNames = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    int skipped = 0, collected = 0;
    boolean hasMore = false;

    try (ITsFileReader reader = new TsFileReaderBuilder().file(filePath).build()) {
      for (TableSchema ts : reader.getAllTableSchema()) {
        if (collected >= limit) {
          hasMore = true;
          break;
        }
        String tblName = ts.getTableName();
        if (fTbls != null && !fTbls.isEmpty() && !fTbls.contains(tblName)) continue;
        List<IMeasurementSchema> cols = ts.getColumnSchemas();
        List<ColumnCategory> categories = ts.getColumnTypes();

        // Identify TAG columns for device identifier construction
        List<Integer> tagIndices = new ArrayList<>();
        List<String> tagNames = new ArrayList<>();
        List<String> qCols = new ArrayList<>();
        List<IMeasurementSchema> selCols = new ArrayList<>();

        for (int i = 0; i < cols.size(); i++) {
          String cn = cols.get(i).getMeasurementName();
          if (fCols == null || fCols.isEmpty() || fCols.contains(cn)) {
            qCols.add(cn);
            selCols.add(cols.get(i));
            // Track TAG columns among selected columns
            if (categories != null
                && i < categories.size()
                && categories.get(i) == ColumnCategory.TAG) {
              tagIndices.add(qCols.size() - 1); // Index in qCols/selCols
              tagNames.add(cn);
            }
          }
        }

        if (qCols.isEmpty()) continue;
        if (colNames.isEmpty()) {
          colNames.add("Time");
          colTypes.add("TIMESTAMP");
          colNames.add("Device");
          colTypes.add("STRING");
          for (IMeasurementSchema c : selCols) {
            colNames.add(c.getMeasurementName());
            colTypes.add(c.getType().toString());
          }
        }
        try (ResultSet rs = reader.query(tblName, qCols, effStart, effEnd)) {
          while (rs.next()) {
            long ts2 = rs.getLong(1);
            Map<String, Object> vals = new HashMap<>();
            List<String> tagValues = new ArrayList<>();
            boolean pass = true;

            for (int i = 0; i < qCols.size(); i++) {
              int idx = i + 2;
              if (rs.isNull(idx)) {
                vals.put(qCols.get(i), null);
                if (tagIndices.contains(i)) {
                  tagValues.add("null");
                }
                continue;
              }
              Object v = extractValue(rs, idx, selCols.get(i).getType());
              vals.put(qCols.get(i), v);
              if (tagIndices.contains(i)) {
                tagValues.add(String.valueOf(v));
              }
              if ((minV != null || maxV != null) && v instanceof Number) {
                double nv = ((Number) v).doubleValue();
                if (minV != null && nv < minV) pass = false;
                if (maxV != null && nv > maxV) pass = false;
              }
            }
            if (!pass) continue;
            if (skipped < offset) {
              skipped++;
              continue;
            }
            if (collected >= limit) {
              hasMore = true;
              break;
            }

            // Construct device identifier: tablename.tagvalue1.tagvalue2...
            String deviceId;
            if (tagValues.isEmpty()) {
              // No TAG columns - tree model style, use table name as device
              deviceId = tblName;
            } else {
              // Table model - construct device ID from table name and TAG values
              deviceId = tblName + "." + String.join(".", tagValues);
            }

            results.add(new DataRow(ts2, deviceId, vals));
            collected++;
          }
        } catch (NoTableException | NoMeasurementException | ReadProcessException e) {
          logger.warn("Error querying {}: {}", tblName, e.getMessage());
        } catch (java.nio.BufferUnderflowException | java.nio.BufferOverflowException e) {
          logger.warn(
              "Buffer error reading table {}, possibly incompatible file version: {}",
              tblName,
              e.getMessage());
        } catch (RuntimeException e) {
          logger.warn("Unexpected error reading table {}: {}", tblName, e.getMessage());
        }
      }
    }
    return new DataReadResult(results, collected, hasMore, colNames, colTypes);
  }

  /** Streams data with filtering, invoking a callback for each row. */
  public int streamData(File filePath, ReadFilter filter, Consumer<DataRow> consumer)
      throws IOException {
    Long st = filter != null ? filter.getStartTime() : null;
    Long et = filter != null ? filter.getEndTime() : null;
    List<String> fTbls = filter != null ? filter.getTables() : null;
    List<String> fCols = filter != null ? filter.getColumns() : null;
    Double minV = filter != null ? filter.getMinValue() : null;
    Double maxV = filter != null ? filter.getMaxValue() : null;
    long effStart = st != null ? st : Long.MIN_VALUE;
    long effEnd = et != null ? et : Long.MAX_VALUE;
    int processed = 0;

    try (ITsFileReader reader = new TsFileReaderBuilder().file(filePath).build()) {
      for (TableSchema ts : reader.getAllTableSchema()) {
        String tblName = ts.getTableName();
        if (fTbls != null && !fTbls.isEmpty() && !fTbls.contains(tblName)) continue;
        List<IMeasurementSchema> cols = ts.getColumnSchemas();
        List<ColumnCategory> categories = ts.getColumnTypes();

        // Identify TAG columns for device identifier construction
        List<Integer> tagIndices = new ArrayList<>();
        List<String> qCols = new ArrayList<>();
        List<IMeasurementSchema> selCols = new ArrayList<>();

        for (int i = 0; i < cols.size(); i++) {
          String cn = cols.get(i).getMeasurementName();
          if (fCols == null || fCols.isEmpty() || fCols.contains(cn)) {
            qCols.add(cn);
            selCols.add(cols.get(i));
            // Track TAG columns among selected columns
            if (categories != null
                && i < categories.size()
                && categories.get(i) == ColumnCategory.TAG) {
              tagIndices.add(qCols.size() - 1);
            }
          }
        }

        if (qCols.isEmpty()) continue;
        try (ResultSet rs = reader.query(tblName, qCols, effStart, effEnd)) {
          while (rs.next()) {
            long ts2 = rs.getLong(1);
            Map<String, Object> vals = new HashMap<>();
            List<String> tagValues = new ArrayList<>();
            boolean pass = true;

            for (int i = 0; i < qCols.size(); i++) {
              int idx = i + 2;
              if (rs.isNull(idx)) {
                vals.put(qCols.get(i), null);
                if (tagIndices.contains(i)) {
                  tagValues.add("null");
                }
                continue;
              }
              Object v = extractValue(rs, idx, selCols.get(i).getType());
              vals.put(qCols.get(i), v);
              if (tagIndices.contains(i)) {
                tagValues.add(String.valueOf(v));
              }
              if ((minV != null || maxV != null) && v instanceof Number) {
                double nv = ((Number) v).doubleValue();
                if (minV != null && nv < minV) pass = false;
                if (maxV != null && nv > maxV) pass = false;
              }
            }
            if (!pass) continue;

            // Construct device identifier
            String deviceId =
                tagValues.isEmpty() ? tblName : tblName + "." + String.join(".", tagValues);
            consumer.accept(new DataRow(ts2, deviceId, vals));
            processed++;
          }
        } catch (NoTableException | NoMeasurementException | ReadProcessException e) {
          logger.warn("Error querying {}: {}", tblName, e.getMessage());
        } catch (java.nio.BufferUnderflowException | java.nio.BufferOverflowException e) {
          logger.warn(
              "Buffer error reading table {}, possibly incompatible file version: {}",
              tblName,
              e.getMessage());
        } catch (RuntimeException e) {
          logger.warn("Unexpected error reading table {}: {}", tblName, e.getMessage());
        }
      }
    }
    return processed;
  }

  /** Reads data for specific timestamps. */
  public DataReadResult readDataByTimestamps(File filePath, List<Long> timestamps)
      throws IOException {
    if (timestamps == null || timestamps.isEmpty()) {
      return new DataReadResult(new ArrayList<>(), 0, false, new ArrayList<>(), new ArrayList<>());
    }
    long minTs = timestamps.stream().min(Long::compare).orElse(Long.MIN_VALUE);
    long maxTs = timestamps.stream().max(Long::compare).orElse(Long.MAX_VALUE);
    var tsSet = new HashSet<>(timestamps);
    List<DataRow> results = new ArrayList<>();
    List<String> colNames = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();

    try (ITsFileReader reader = new TsFileReaderBuilder().file(filePath).build()) {
      for (TableSchema ts : reader.getAllTableSchema()) {
        String tblName = ts.getTableName();
        List<IMeasurementSchema> cols = ts.getColumnSchemas();
        List<ColumnCategory> categories = ts.getColumnTypes();

        // Identify TAG columns for device identifier construction
        List<Integer> tagIndices = new ArrayList<>();
        List<String> qCols = new ArrayList<>();

        for (int i = 0; i < cols.size(); i++) {
          qCols.add(cols.get(i).getMeasurementName());
          if (categories != null
              && i < categories.size()
              && categories.get(i) == ColumnCategory.TAG) {
            tagIndices.add(i);
          }
        }

        if (qCols.isEmpty()) continue;
        if (colNames.isEmpty()) {
          colNames.add("Time");
          colTypes.add("TIMESTAMP");
          colNames.add("Device");
          colTypes.add("STRING");
          for (IMeasurementSchema c : cols) {
            colNames.add(c.getMeasurementName());
            colTypes.add(c.getType().toString());
          }
        }
        try (ResultSet rs = reader.query(tblName, qCols, minTs, maxTs)) {
          while (rs.next()) {
            long ts2 = rs.getLong(1);
            if (!tsSet.contains(ts2)) continue;
            Map<String, Object> vals = new HashMap<>();
            List<String> tagValues = new ArrayList<>();

            for (int i = 0; i < qCols.size(); i++) {
              int idx = i + 2;
              if (rs.isNull(idx)) {
                vals.put(qCols.get(i), null);
                if (tagIndices.contains(i)) {
                  tagValues.add("null");
                }
                continue;
              }
              Object value = extractValue(rs, idx, cols.get(i).getType());
              vals.put(qCols.get(i), value);
              if (tagIndices.contains(i)) {
                tagValues.add(String.valueOf(value));
              }
            }

            // Construct device identifier
            String deviceId =
                tagValues.isEmpty() ? tblName : tblName + "." + String.join(".", tagValues);
            results.add(new DataRow(ts2, deviceId, vals));
          }
        } catch (NoTableException | NoMeasurementException | ReadProcessException e) {
          logger.warn("Error querying {}: {}", tblName, e.getMessage());
        } catch (java.nio.BufferUnderflowException | java.nio.BufferOverflowException e) {
          logger.warn(
              "Buffer error reading table {}, possibly incompatible file version: {}",
              tblName,
              e.getMessage());
        } catch (RuntimeException e) {
          logger.warn("Unexpected error reading table {}: {}", tblName, e.getMessage());
        }
      }
    }
    return new DataReadResult(results, results.size(), false, colNames, colTypes);
  }

  /** Gets the list of available tables. */
  public List<String> getTableNames(File filePath) throws IOException {
    List<String> names = new ArrayList<>();
    try (ITsFileReader reader = new TsFileReaderBuilder().file(filePath).build()) {
      for (TableSchema ts : reader.getAllTableSchema()) names.add(ts.getTableName());
    }
    return names;
  }

  /** Gets the column schema for a specific table. */
  public List<Map<String, String>> getTableColumns(File filePath, String tableName)
      throws IOException {
    List<Map<String, String>> cols = new ArrayList<>();
    try (ITsFileReader reader = new TsFileReaderBuilder().file(filePath).build()) {
      var opt = reader.getTableSchemas(tableName);
      if (opt.isPresent()) {
        for (IMeasurementSchema c : opt.get().getColumnSchemas()) {
          Map<String, String> info = new HashMap<>();
          info.put("name", c.getMeasurementName());
          info.put("type", c.getType().toString());
          cols.add(info);
        }
      }
    }
    return cols;
  }

  /** Counts total rows in a time range. */
  public long countRows(File filePath, Long startTime, Long endTime) throws IOException {
    long effStart = startTime != null ? startTime : Long.MIN_VALUE;
    long effEnd = endTime != null ? endTime : Long.MAX_VALUE;
    long total = 0;
    try (ITsFileReader reader = new TsFileReaderBuilder().file(filePath).build()) {
      for (TableSchema ts : reader.getAllTableSchema()) {
        List<String> qCols = new ArrayList<>();
        for (IMeasurementSchema c : ts.getColumnSchemas()) qCols.add(c.getMeasurementName());
        if (qCols.isEmpty()) continue;
        try (ResultSet rs = reader.query(ts.getTableName(), qCols, effStart, effEnd)) {
          while (rs.next()) total++;
        } catch (NoTableException | NoMeasurementException | ReadProcessException e) {
          logger.warn("Error counting: {}", e.getMessage());
        } catch (java.nio.BufferUnderflowException | java.nio.BufferOverflowException e) {
          logger.warn(
              "Buffer error counting rows in table {}: {}", ts.getTableName(), e.getMessage());
        } catch (RuntimeException e) {
          logger.warn(
              "Unexpected error counting rows in table {}: {}", ts.getTableName(), e.getMessage());
        }
      }
    }
    return total;
  }

  /**
   * Reads data from a tree model TsFile using TsFileReader with QueryExpression.
   *
   * <p>Tree model files don't have TableSchema, so we use the TsFileReader API with QueryExpression
   * to read device/measurement structure and data.
   */
  private DataReadResult readTreeModelData(
      File filePath, long effStart, long effEnd, int limit, int offset) throws IOException {
    List<DataRow> results = new ArrayList<>();
    List<String> colNames = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    int skipped = 0, collected = 0;
    boolean hasMore = false;

    try (TsFileSequenceReader seqReader = new TsFileSequenceReader(filePath.getAbsolutePath());
        TsFileReader tsFileReader = new TsFileReader(seqReader)) {

      // Get all devices and their measurements
      Map<IDeviceID, Map<String, TSDataType>> deviceMeasurements = new HashMap<>();

      for (IDeviceID device : seqReader.getAllDevices()) {
        Map<String, List<ChunkMetadata>> seriesMetaData =
            seqReader.readChunkMetadataInDevice(device);
        Map<String, TSDataType> measurements = new HashMap<>();

        for (Map.Entry<String, List<ChunkMetadata>> entry : seriesMetaData.entrySet()) {
          if (!entry.getValue().isEmpty()) {
            TSDataType dataType = entry.getValue().get(0).getDataType();
            // Skip VECTOR type (used for aligned time series time column)
            if (dataType != TSDataType.VECTOR) {
              measurements.put(entry.getKey(), dataType);
            }
          }
        }
        deviceMeasurements.put(device, measurements);
      }

      // Build column names from all unique measurements
      Set<String> allMeasurements = new HashSet<>();
      Map<String, TSDataType> measurementTypes = new HashMap<>();
      for (Map<String, TSDataType> measurements : deviceMeasurements.values()) {
        allMeasurements.addAll(measurements.keySet());
        measurementTypes.putAll(measurements);
      }

      if (!allMeasurements.isEmpty()) {
        colNames.add("Time");
        colTypes.add("TIMESTAMP");
        colNames.add("Device");
        colTypes.add("STRING");
        for (String measurement : allMeasurements) {
          colNames.add(measurement);
          TSDataType type = measurementTypes.get(measurement);
          colTypes.add(type != null ? type.toString() : "UNKNOWN");
        }
      }

      // Read data for each device using TsFileReader with QueryExpression
      for (Map.Entry<IDeviceID, Map<String, TSDataType>> entry : deviceMeasurements.entrySet()) {
        if (collected >= limit) {
          hasMore = true;
          break;
        }

        IDeviceID device = entry.getKey();
        String deviceName = device.toString();
        Map<String, TSDataType> measurements = entry.getValue();

        if (measurements.isEmpty()) {
          continue;
        }

        // Build paths for query (only non-VECTOR measurements)
        ArrayList<Path> paths = new ArrayList<>();
        List<String> measurementNames = new ArrayList<>(measurements.keySet());
        for (String measurement : measurementNames) {
          paths.add(new Path(deviceName, measurement, true));
        }

        // Build time filter expression
        IExpression timeFilter = null;
        if (effStart != Long.MIN_VALUE && effEnd != Long.MAX_VALUE) {
          timeFilter =
              BinaryExpression.and(
                  new GlobalTimeExpression(TimeFilterApi.gtEq(effStart)),
                  new GlobalTimeExpression(TimeFilterApi.ltEq(effEnd)));
        } else if (effStart != Long.MIN_VALUE) {
          timeFilter = new GlobalTimeExpression(TimeFilterApi.gtEq(effStart));
        } else if (effEnd != Long.MAX_VALUE) {
          timeFilter = new GlobalTimeExpression(TimeFilterApi.ltEq(effEnd));
        }

        // Execute query
        QueryExpression queryExpression = QueryExpression.create(paths, timeFilter);
        QueryDataSet queryDataSet = tsFileReader.query(queryExpression);

        while (queryDataSet.hasNext()) {
          if (skipped < offset) {
            skipped++;
            queryDataSet.next();
            continue;
          }
          if (collected >= limit) {
            hasMore = true;
            break;
          }

          RowRecord record = queryDataSet.next();
          long timestamp = record.getTimestamp();
          Map<String, Object> values = new HashMap<>();

          List<org.apache.tsfile.read.common.Field> fields = record.getFields();
          for (int fieldIndex = 0;
              fieldIndex < fields.size() && fieldIndex < measurementNames.size();
              fieldIndex++) {
            String measurement = measurementNames.get(fieldIndex);
            org.apache.tsfile.read.common.Field field = fields.get(fieldIndex);
            if (field != null && field.getDataType() != null) {
              Object val = field.getObjectValue(field.getDataType());
              // Convert Binary objects to String for TEXT/STRING/BLOB types
              if (val instanceof org.apache.tsfile.utils.Binary) {
                val = val.toString();
              }
              values.put(measurement, val);
            } else {
              values.put(measurement, null);
            }
          }

          results.add(new DataRow(timestamp, deviceName, values));
          collected++;
        }
      }
    }

    return new DataReadResult(results, collected, hasMore, colNames, colTypes);
  }

  private Object extractValue(ResultSet rs, int idx, TSDataType type) {
    try {
      return switch (type) {
        case BOOLEAN -> rs.getBoolean(idx);
        case INT32 -> rs.getInt(idx);
        case INT64, TIMESTAMP -> rs.getLong(idx);
        case FLOAT -> rs.getFloat(idx);
        case DOUBLE -> rs.getDouble(idx);
        case TEXT, STRING -> rs.getString(idx);
        case BLOB -> {
          var binary = rs.getBinary(idx);
          yield binary != null ? binary.toString() : null;
        }
        case DATE -> rs.getDate(idx);
        default -> rs.getString(idx);
      };
    } catch (Exception e) {
      return null;
    }
  }
}
