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

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.file.metadata.ChunkMetadata;
import org.apache.tsfile.file.metadata.IChunkMetadata;
import org.apache.tsfile.file.metadata.IDeviceID;
import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.file.metadata.TimeseriesMetadata;
import org.apache.tsfile.file.metadata.statistics.Statistics;
import org.apache.tsfile.read.TsFileSequenceReader;
import org.apache.tsfile.read.v4.ITsFileReader;
import org.apache.tsfile.read.v4.TsFileReaderBuilder;
import org.apache.tsfile.write.schema.IMeasurementSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.apache.tsfile.viewer.dto.ChunkDTO;
import org.apache.tsfile.viewer.dto.MeasurementDTO;
import org.apache.tsfile.viewer.dto.RowGroupDTO;

/**
 * Utility class for parsing TsFile metadata.
 *
 * <p>Provides methods to extract metadata from TsFile files including version, time range,
 * device/measurement counts, measurement schema details, RowGroup metadata, and Chunk metadata.
 *
 * <p>Validates: Requirement 2.1, 2.3, 2.4, 2.5 (Metadata parsing)
 */
@Component
public class TsFileParser {

  private static final Logger logger = LoggerFactory.getLogger(TsFileParser.class);

  /** Result class containing parsed TsFile metadata. */
  public static class ParsedMetadata {
    private final String version;
    private final long startTime;
    private final long endTime;
    private final int deviceCount;
    private final int measurementCount;
    private final int rowGroupCount;
    private final int chunkCount;

    public ParsedMetadata(
        String version,
        long startTime,
        long endTime,
        int deviceCount,
        int measurementCount,
        int rowGroupCount,
        int chunkCount) {
      this.version = version;
      this.startTime = startTime;
      this.endTime = endTime;
      this.deviceCount = deviceCount;
      this.measurementCount = measurementCount;
      this.rowGroupCount = rowGroupCount;
      this.chunkCount = chunkCount;
    }

    public String getVersion() {
      return version;
    }

    public long getStartTime() {
      return startTime;
    }

    public long getEndTime() {
      return endTime;
    }

    public int getDeviceCount() {
      return deviceCount;
    }

    public int getMeasurementCount() {
      return measurementCount;
    }

    public int getRowGroupCount() {
      return rowGroupCount;
    }

    public int getChunkCount() {
      return chunkCount;
    }
  }

  /** Result class containing measurement schema details. */
  public static class MeasurementInfo {
    private final String name;
    private final String dataType;
    private final String encoding;
    private final String compression;
    private final String columnCategory;

    public MeasurementInfo(String name, String dataType, String encoding, String compression) {
      this(name, dataType, encoding, compression, "FIELD");
    }

    public MeasurementInfo(
        String name, String dataType, String encoding, String compression, String columnCategory) {
      this.name = name;
      this.dataType = dataType;
      this.encoding = encoding;
      this.compression = compression;
      this.columnCategory = columnCategory;
    }

    public String getName() {
      return name;
    }

    public String getDataType() {
      return dataType;
    }

    public String getEncoding() {
      return encoding;
    }

    public String getCompression() {
      return compression;
    }

    public String getColumnCategory() {
      return columnCategory;
    }
  }

  /** Result class containing RowGroup metadata. */
  public static class RowGroupInfo {
    private final int index;
    private final String device;
    private final long startTime;
    private final long endTime;
    private final int chunkCount;

    public RowGroupInfo(int index, String device, long startTime, long endTime, int chunkCount) {
      this.index = index;
      this.device = device;
      this.startTime = startTime;
      this.endTime = endTime;
      this.chunkCount = chunkCount;
    }

    public int getIndex() {
      return index;
    }

    public String getDevice() {
      return device;
    }

    public long getStartTime() {
      return startTime;
    }

    public long getEndTime() {
      return endTime;
    }

    public int getChunkCount() {
      return chunkCount;
    }
  }

  /** Result class containing Chunk metadata with compression stats. */
  public static class ChunkInfo {
    private final String measurement;
    private final long offset;
    private final long size;
    private final double compressionRatio;
    private final String device;
    private final long startTime;
    private final long endTime;
    private final long numOfPoints;
    private final String dataType;
    private final String encoding;
    private final String compression;
    private final String minValue;
    private final String maxValue;

    public ChunkInfo(
        String measurement,
        long offset,
        long size,
        double compressionRatio,
        String device,
        long startTime,
        long endTime,
        long numOfPoints) {
      this(
          measurement,
          offset,
          size,
          compressionRatio,
          device,
          startTime,
          endTime,
          numOfPoints,
          null,
          null,
          null,
          null,
          null);
    }

    public ChunkInfo(
        String measurement,
        long offset,
        long size,
        double compressionRatio,
        String device,
        long startTime,
        long endTime,
        long numOfPoints,
        String dataType,
        String encoding,
        String compression,
        String minValue,
        String maxValue) {
      this.measurement = measurement;
      this.offset = offset;
      this.size = size;
      this.compressionRatio = compressionRatio;
      this.device = device;
      this.startTime = startTime;
      this.endTime = endTime;
      this.numOfPoints = numOfPoints;
      this.dataType = dataType;
      this.encoding = encoding;
      this.compression = compression;
      this.minValue = minValue;
      this.maxValue = maxValue;
    }

    public String getMeasurement() {
      return measurement;
    }

    public long getOffset() {
      return offset;
    }

    public long getSize() {
      return size;
    }

    public double getCompressionRatio() {
      return compressionRatio;
    }

    public String getDevice() {
      return device;
    }

    public long getStartTime() {
      return startTime;
    }

    public long getEndTime() {
      return endTime;
    }

    public long getNumOfPoints() {
      return numOfPoints;
    }

    public String getDataType() {
      return dataType;
    }

    public String getEncoding() {
      return encoding;
    }

    public String getCompression() {
      return compression;
    }

    public String getMinValue() {
      return minValue;
    }

    public String getMaxValue() {
      return maxValue;
    }
  }

  /** Result class containing table schema information for V4 table model. */
  public static class TableInfo {
    private final String tableName;
    private final List<MeasurementInfo> tagColumns;
    private final List<MeasurementInfo> fieldColumns;

    public TableInfo(
        String tableName, List<MeasurementInfo> tagColumns, List<MeasurementInfo> fieldColumns) {
      this.tableName = tableName;
      this.tagColumns = tagColumns;
      this.fieldColumns = fieldColumns;
    }

    public String getTableName() {
      return tableName;
    }

    public List<MeasurementInfo> getTagColumns() {
      return tagColumns;
    }

    public List<MeasurementInfo> getFieldColumns() {
      return fieldColumns;
    }

    public int getTotalColumns() {
      return tagColumns.size() + fieldColumns.size();
    }
  }

  /**
   * Parses metadata from a TsFile.
   *
   * <p>Extracts version, time range, device count, measurement count, RowGroup count, and Chunk
   * count from the specified TsFile.
   *
   * @param filePath path to the TsFile
   * @return BasicMetadata containing the extracted metadata
   * @throws IOException if the file cannot be read or is not a valid TsFile
   */
  public BasicMetadata parseMetadata(Path filePath) throws IOException {
    logger.debug("Parsing metadata from TsFile: {}", filePath);

    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath.toString())) {
      ParsedMetadata parsed = parseMetadataInternal(reader);
      return new BasicMetadata(
          parsed.getVersion(),
          parsed.getStartTime(),
          parsed.getEndTime(),
          parsed.getDeviceCount(),
          parsed.getMeasurementCount(),
          parsed.getRowGroupCount(),
          parsed.getChunkCount());
    }
  }

  /**
   * Parses metadata from an existing TsFileSequenceReader (internal method).
   *
   * @param reader the TsFileSequenceReader to use
   * @return ParsedMetadata containing the extracted metadata
   * @throws IOException if the file cannot be read
   */
  private ParsedMetadata parseMetadataInternal(TsFileSequenceReader reader) throws IOException {
    // Get version
    byte versionNumber = reader.readVersionNumber();
    String version = "V" + versionNumber;

    // Get all devices
    List<IDeviceID> devices = reader.getAllDevices();
    int deviceCount = devices.size();

    // Get all timeseries metadata to calculate measurements, time range, and counts
    Map<IDeviceID, List<TimeseriesMetadata>> allTimeseriesMetadata =
        reader.getAllTimeseriesMetadata(true);

    // Calculate unique measurements and time range
    Set<String> uniqueMeasurements = new HashSet<>();
    long minStartTime = Long.MAX_VALUE;
    long maxEndTime = Long.MIN_VALUE;
    int totalChunkCount = 0;
    int rowGroupCount = 0;

    for (Map.Entry<IDeviceID, List<TimeseriesMetadata>> entry : allTimeseriesMetadata.entrySet()) {
      List<TimeseriesMetadata> timeseriesMetadataList = entry.getValue();
      rowGroupCount++; // Each device entry represents a RowGroup

      for (TimeseriesMetadata tsMetadata : timeseriesMetadataList) {
        uniqueMeasurements.add(tsMetadata.getMeasurementId());

        // Get time range from statistics
        Statistics<? extends Serializable> stats = tsMetadata.getStatistics();
        if (stats != null) {
          if (stats.getStartTime() < minStartTime) {
            minStartTime = stats.getStartTime();
          }
          if (stats.getEndTime() > maxEndTime) {
            maxEndTime = stats.getEndTime();
          }
        }

        // Count chunks
        List<IChunkMetadata> chunkMetadataList = tsMetadata.getChunkMetadataList();
        if (chunkMetadataList != null) {
          totalChunkCount += chunkMetadataList.size();
        }
      }
    }

    // Handle case where no data exists
    if (minStartTime == Long.MAX_VALUE) {
      minStartTime = 0;
    }
    if (maxEndTime == Long.MIN_VALUE) {
      maxEndTime = 0;
    }

    logger.debug(
        "Parsed metadata: version={}, devices={}, measurements={}, rowGroups={}, chunks={}",
        version,
        deviceCount,
        uniqueMeasurements.size(),
        rowGroupCount,
        totalChunkCount);

    return new ParsedMetadata(
        version,
        minStartTime,
        maxEndTime,
        deviceCount,
        uniqueMeasurements.size(),
        rowGroupCount,
        totalChunkCount);
  }

  /**
   * Parses measurement schema details from a TsFileSequenceReader.
   *
   * <p>Extracts measurement name, data type, encoding, and compression algorithm for each unique
   * measurement in the TsFile.
   *
   * @param reader the TsFileSequenceReader to use
   * @return list of MeasurementInfo containing measurement schema details
   * @throws IOException if the file cannot be read
   */
  /**
   * Parse measurement metadata from a TsFile.
   *
   * <p>For V4 table model files, extracts column categories (TAG/FIELD) from TableSchema. For tree
   * model files, all measurements default to FIELD category.
   *
   * @param reader TsFile reader
   * @return List of measurement information
   * @throws IOException If reading fails
   */
  public List<MeasurementInfo> parseMeasurements(TsFileSequenceReader reader) throws IOException {
    logger.debug("Parsing measurements from TsFile");

    Map<String, MeasurementInfo> measurementMap = new LinkedHashMap<>();

    // Try to read as V4 table model first to get column categories and schema info
    Map<String, ColumnCategory> columnCategories = new HashMap<>();
    Map<String, IMeasurementSchema> columnSchemas = new HashMap<>();
    boolean isTableModel = false;
    try {
      String tsfilePath = reader.getFileName();
      Path path = Paths.get(tsfilePath);
      logger.debug("Attempting to read TableSchema from: {}", tsfilePath);
      try (ITsFileReader v4Reader = new TsFileReaderBuilder().file(path.toFile()).build()) {
        List<TableSchema> schemas = v4Reader.getAllTableSchema();
        if (schemas != null && !schemas.isEmpty()) {
          isTableModel = true;
          logger.debug("Found {} table schemas in V4 format", schemas.size());
          for (TableSchema schema : schemas) {
            logger.debug("Processing table schema: {}", schema.getTableName());
            List<IMeasurementSchema> schemaList = schema.getColumnSchemas();
            List<ColumnCategory> categories = schema.getColumnTypes();

            // Column schemas and categories lists are parallel - same index corresponds to
            // same column
            if (schemaList != null && categories != null) {
              for (int i = 0; i < schemaList.size() && i < categories.size(); i++) {
                IMeasurementSchema columnSchema = schemaList.get(i);
                ColumnCategory category = categories.get(i);
                String columnName = columnSchema.getMeasurementName();

                if (category != null) {
                  columnCategories.put(columnName, category);
                  columnSchemas.put(columnName, columnSchema);
                  logger.debug("  Column {} has category {}", columnName, category);

                  // For table model, add TAG columns directly since they won't appear in
                  // TimeseriesMetadata
                  if (category == ColumnCategory.TAG) {
                    String dataType =
                        columnSchema.getType() != null
                            ? columnSchema.getType().toString()
                            : "STRING";
                    String encoding =
                        columnSchema.getEncodingType() != null
                            ? columnSchema.getEncodingType().toString()
                            : "PLAIN";
                    String compression =
                        columnSchema.getCompressor() != null
                            ? columnSchema.getCompressor().toString()
                            : "UNCOMPRESSED";

                    measurementMap.put(
                        columnName,
                        new MeasurementInfo(columnName, dataType, encoding, compression, "TAG"));
                  }
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      logger.debug("Not a V4 table model file or failed to read TableSchema: {}", e.getMessage());
    }

    // Parse measurements from TimeseriesMetadata
    Map<IDeviceID, List<TimeseriesMetadata>> allTimeseriesMetadata =
        reader.getAllTimeseriesMetadata(true);

    for (Map.Entry<IDeviceID, List<TimeseriesMetadata>> entry : allTimeseriesMetadata.entrySet()) {
      List<TimeseriesMetadata> timeseriesMetadataList = entry.getValue();

      for (TimeseriesMetadata tsMetadata : timeseriesMetadataList) {
        String measurementId = tsMetadata.getMeasurementId();

        // Skip if already processed
        if (measurementMap.containsKey(measurementId)) {
          continue;
        }

        // Get data type from TimeseriesMetadata
        String dataType = tsMetadata.getTsDataType().toString();

        // Get encoding and compression - prioritize TableSchema for V4 table model
        String encoding = "UNKNOWN";
        String compression = "UNKNOWN";

        // For V4 table model, try to get from TableSchema first
        IMeasurementSchema tableSchema = columnSchemas.get(measurementId);
        if (tableSchema != null) {
          if (tableSchema.getEncodingType() != null) {
            encoding = tableSchema.getEncodingType().toString();
          }
          if (tableSchema.getCompressor() != null) {
            compression = tableSchema.getCompressor().toString();
          }
          logger.debug(
              "Got encoding/compression from TableSchema for {}: {}/{}",
              measurementId,
              encoding,
              compression);
        } else {
          // Fallback to ChunkHeader for tree model files
          // Read ChunkHeader from file to get encoding and compression
          List<IChunkMetadata> chunkMetadataList = tsMetadata.getChunkMetadataList();
          if (chunkMetadataList != null && !chunkMetadataList.isEmpty()) {
            IChunkMetadata firstChunk = chunkMetadataList.get(0);
            if (firstChunk instanceof ChunkMetadata chunkMeta) {
              try {
                // Read the chunk which includes the header with encoding/compression info
                org.apache.tsfile.read.common.Chunk chunk = reader.readMemChunk(chunkMeta);
                if (chunk != null && chunk.getHeader() != null) {
                  org.apache.tsfile.file.header.ChunkHeader chunkHeader = chunk.getHeader();
                  if (chunkHeader.getEncodingType() != null) {
                    encoding = chunkHeader.getEncodingType().toString();
                  }
                  if (chunkHeader.getCompressionType() != null) {
                    compression = chunkHeader.getCompressionType().toString();
                  }
                  logger.debug(
                      "Read encoding/compression from ChunkHeader for {}: {}/{}",
                      measurementId,
                      encoding,
                      compression);
                }
              } catch (Exception e) {
                logger.debug(
                    "Could not read ChunkHeader for measurement {}: {}",
                    measurementId,
                    e.getMessage());
              }
            }
          }
        }

        // Get column category from V4 TableSchema if available
        String columnCategory = "FIELD"; // Default to FIELD
        ColumnCategory category = columnCategories.get(measurementId);
        if (category != null) {
          columnCategory = category.toString();
        }

        measurementMap.put(
            measurementId,
            new MeasurementInfo(measurementId, dataType, encoding, compression, columnCategory));
      }
    }

    logger.debug("Parsed {} unique measurements", measurementMap.size());
    return new ArrayList<>(measurementMap.values());
  }

  /**
   * Parses table schemas from a V4 table model TsFile.
   *
   * <p>Extracts table structures with TAG and FIELD columns grouped by table. Returns empty list
   * for tree model files.
   *
   * @param reader TsFile reader
   * @return List of TableInfo containing table schemas
   * @throws IOException If reading fails
   */
  public List<TableInfo> parseTables(TsFileSequenceReader reader) throws IOException {
    logger.debug("Parsing table schemas from TsFile");

    List<TableInfo> tableInfos = new ArrayList<>();

    try {
      String tsfilePath = reader.getFileName();
      Path path = Paths.get(tsfilePath);
      logger.debug("Attempting to read TableSchema from: {}", tsfilePath);

      try (ITsFileReader v4Reader = new TsFileReaderBuilder().file(path.toFile()).build()) {
        List<TableSchema> schemas = v4Reader.getAllTableSchema();

        if (schemas == null || schemas.isEmpty()) {
          logger.debug("No table schemas found - this is a tree model file");
          return tableInfos;
        }

        logger.debug("Found {} table schemas in V4 format", schemas.size());

        for (TableSchema schema : schemas) {
          String tableName = schema.getTableName();
          logger.debug("Processing table: {}", tableName);

          List<MeasurementInfo> tagColumns = new ArrayList<>();
          List<MeasurementInfo> fieldColumns = new ArrayList<>();

          List<IMeasurementSchema> columnSchemas = schema.getColumnSchemas();
          List<ColumnCategory> categories = schema.getColumnTypes();

          if (columnSchemas != null && categories != null) {
            for (int i = 0; i < columnSchemas.size() && i < categories.size(); i++) {
              IMeasurementSchema columnSchema = columnSchemas.get(i);
              ColumnCategory category = categories.get(i);
              String columnName = columnSchema.getMeasurementName();

              String dataType =
                  columnSchema.getType() != null ? columnSchema.getType().toString() : "UNKNOWN";
              String encoding =
                  columnSchema.getEncodingType() != null
                      ? columnSchema.getEncodingType().toString()
                      : "PLAIN";
              String compression =
                  columnSchema.getCompressor() != null
                      ? columnSchema.getCompressor().toString()
                      : "UNCOMPRESSED";

              MeasurementInfo columnInfo =
                  new MeasurementInfo(
                      columnName, dataType, encoding, compression, category.toString());

              if (category == ColumnCategory.TAG) {
                tagColumns.add(columnInfo);
                logger.debug("  TAG column: {}", columnName);
              } else if (category == ColumnCategory.FIELD) {
                fieldColumns.add(columnInfo);
                logger.debug("  FIELD column: {}", columnName);
              }
            }
          }

          TableInfo tableInfo = new TableInfo(tableName, tagColumns, fieldColumns);
          tableInfos.add(tableInfo);

          logger.debug(
              "Table '{}': {} TAG columns, {} FIELD columns, {} total",
              tableName,
              tagColumns.size(),
              fieldColumns.size(),
              tableInfo.getTotalColumns());
        }
      }
    } catch (Exception e) {
      logger.debug("Not a V4 table model file or failed to read tables: {}", e.getMessage());
    }

    logger.debug("Parsed {} tables total", tableInfos.size());
    return tableInfos;
  }

  /**
   * Parses RowGroup metadata from a TsFileSequenceReader.
   *
   * <p>Extracts RowGroup index, device, time range, and chunk count for each RowGroup in the
   * TsFile.
   *
   * @param reader the TsFileSequenceReader to use
   * @return list of RowGroupInfo containing RowGroup metadata
   * @throws IOException if the file cannot be read
   */
  public List<RowGroupInfo> parseRowGroups(TsFileSequenceReader reader) throws IOException {
    logger.debug("Parsing RowGroups from TsFile");

    List<RowGroupInfo> rowGroups = new ArrayList<>();
    Map<IDeviceID, List<TimeseriesMetadata>> allTimeseriesMetadata =
        reader.getAllTimeseriesMetadata(true);

    int index = 0;
    for (Map.Entry<IDeviceID, List<TimeseriesMetadata>> entry : allTimeseriesMetadata.entrySet()) {
      IDeviceID device = entry.getKey();
      List<TimeseriesMetadata> timeseriesMetadataList = entry.getValue();

      long minStartTime = Long.MAX_VALUE;
      long maxEndTime = Long.MIN_VALUE;
      int chunkCount = 0;

      for (TimeseriesMetadata tsMetadata : timeseriesMetadataList) {
        Statistics<? extends Serializable> stats = tsMetadata.getStatistics();
        if (stats != null) {
          if (stats.getStartTime() < minStartTime) {
            minStartTime = stats.getStartTime();
          }
          if (stats.getEndTime() > maxEndTime) {
            maxEndTime = stats.getEndTime();
          }
        }

        List<IChunkMetadata> chunkMetadataList = tsMetadata.getChunkMetadataList();
        if (chunkMetadataList != null) {
          chunkCount += chunkMetadataList.size();
        }
      }

      // Handle case where no data exists
      if (minStartTime == Long.MAX_VALUE) {
        minStartTime = 0;
      }
      if (maxEndTime == Long.MIN_VALUE) {
        maxEndTime = 0;
      }

      rowGroups.add(
          new RowGroupInfo(index++, device.toString(), minStartTime, maxEndTime, chunkCount));
    }

    logger.debug("Parsed {} RowGroups", rowGroups.size());
    return rowGroups;
  }

  /**
   * Parses Chunk metadata from a TsFileSequenceReader.
   *
   * <p>Extracts measurement, file offset, size, compression ratio, device, time range, and point
   * count for each Chunk in the TsFile.
   *
   * @param reader the TsFileSequenceReader to use
   * @return list of ChunkInfo containing Chunk metadata with compression stats
   * @throws IOException if the file cannot be read
   */
  public List<ChunkInfo> parseChunks(TsFileSequenceReader reader) throws IOException {
    logger.debug("Parsing Chunks from TsFile");

    List<ChunkInfo> chunks = new ArrayList<>();
    Map<IDeviceID, List<TimeseriesMetadata>> allTimeseriesMetadata =
        reader.getAllTimeseriesMetadata(true);

    for (Map.Entry<IDeviceID, List<TimeseriesMetadata>> entry : allTimeseriesMetadata.entrySet()) {
      IDeviceID device = entry.getKey();
      List<TimeseriesMetadata> timeseriesMetadataList = entry.getValue();

      for (TimeseriesMetadata tsMetadata : timeseriesMetadataList) {
        String measurementId = tsMetadata.getMeasurementId();
        List<IChunkMetadata> chunkMetadataList = tsMetadata.getChunkMetadataList();

        if (chunkMetadataList == null) {
          continue;
        }

        for (IChunkMetadata iChunkMeta : chunkMetadataList) {
          if (!(iChunkMeta instanceof ChunkMetadata chunkMeta)) {
            continue;
          }

          long offset = chunkMeta.getOffsetOfChunkHeader();
          Statistics<? extends Serializable> stats = chunkMeta.getStatistics();

          long startTime = stats != null ? stats.getStartTime() : 0;
          long endTime = stats != null ? stats.getEndTime() : 0;
          long numOfPoints = chunkMeta.getNumOfPoints();

          // Get data type
          String dataType =
              chunkMeta.getDataType() != null ? chunkMeta.getDataType().toString() : "UNKNOWN";

          // Get encoding and compression from ChunkHeader
          String encoding = "UNKNOWN";
          String compression = "UNKNOWN";
          try {
            org.apache.tsfile.read.common.Chunk chunk = reader.readMemChunk(chunkMeta);
            if (chunk != null && chunk.getHeader() != null) {
              org.apache.tsfile.file.header.ChunkHeader chunkHeader = chunk.getHeader();
              if (chunkHeader.getEncodingType() != null) {
                encoding = chunkHeader.getEncodingType().toString();
              }
              if (chunkHeader.getCompressionType() != null) {
                compression = chunkHeader.getCompressionType().toString();
              }
            }
          } catch (Exception e) {
            logger.debug(
                "Could not read ChunkHeader for chunk at offset {}: {}", offset, e.getMessage());
          }

          // Get min/max values from statistics
          String minValue = null;
          String maxValue = null;
          if (stats != null) {
            try {
              Object min = stats.getMinValue();
              Object max = stats.getMaxValue();
              minValue = min != null ? min.toString() : null;
              maxValue = max != null ? max.toString() : null;
            } catch (Exception e) {
              logger.debug("Could not get min/max values from statistics: {}", e.getMessage());
            }
          }

          // Calculate compression ratio
          double compressionRatio = calculateCompressionRatio(chunkMeta, numOfPoints);

          // Size is estimated from the chunk metadata
          long size = estimateChunkSize(chunkMeta);

          chunks.add(
              new ChunkInfo(
                  measurementId,
                  offset,
                  size,
                  compressionRatio,
                  device.toString(),
                  startTime,
                  endTime,
                  numOfPoints,
                  dataType,
                  encoding,
                  compression,
                  minValue,
                  maxValue));
        }
      }
    }

    logger.debug("Parsed {} Chunks", chunks.size());
    return chunks;
  }

  /**
   * Calculates an estimated compression ratio for a chunk.
   *
   * <p>The compression ratio is estimated based on the data type and number of points. A ratio
   * greater than 1.0 indicates compression benefit.
   *
   * @param chunkMeta the chunk metadata
   * @param numOfPoints number of data points in the chunk
   * @return estimated compression ratio
   */
  private double calculateCompressionRatio(ChunkMetadata chunkMeta, long numOfPoints) {
    if (numOfPoints == 0) {
      return 1.0;
    }

    // Estimate uncompressed size based on data type
    int bytesPerPoint =
        switch (chunkMeta.getDataType()) {
          case BOOLEAN -> 1;
          case INT32, FLOAT -> 4;
          case INT64, DOUBLE, TIMESTAMP -> 8;
          case TEXT, STRING, BLOB -> 32; // Average estimate for variable-length types
          default -> 8;
        };

    // Add 8 bytes for timestamp per point
    long estimatedUncompressedSize = numOfPoints * (bytesPerPoint + 8);

    // Get the serialized size from statistics as a proxy for compressed size
    Statistics<? extends Serializable> stats = chunkMeta.getStatistics();
    int statsSize = stats != null ? stats.getSerializedSize() : 0;

    // Use the chunk metadata serialized size as an approximation
    // This is not exact but provides a reasonable estimate
    int chunkMetaSize = chunkMeta.serializedSize(true);

    if (chunkMetaSize > 0) {
      return (double) estimatedUncompressedSize / (chunkMetaSize + statsSize);
    }

    return 1.0;
  }

  /**
   * Estimates the size of a chunk based on its metadata.
   *
   * @param chunkMeta the chunk metadata
   * @return estimated chunk size in bytes
   */
  private long estimateChunkSize(ChunkMetadata chunkMeta) {
    // The serialized size gives us a reasonable estimate
    return chunkMeta.serializedSize(true);
  }

  // ============== Unified Parse Method ==============

  /**
   * Result of parsing all metadata from a TsFile in a single pass. Opens the file only once,
   * avoiding the overhead of 5+ separate file opens that the individual parse methods incur.
   */
  public record AllMetadata(
      BasicMetadata basic,
      List<MeasurementDTO> measurements,
      List<RowGroupDTO> rowGroups,
      List<ChunkDTO> chunks,
      List<org.apache.tsfile.viewer.dto.TableDTO> tables) {}

  /**
   * Parses all metadata from a TsFile in a single pass, opening the file only once.
   *
   * <p>This is significantly more efficient than calling {@link #parseMetadata(String)}, {@link
   * #parseMeasurements(String)}, {@link #parseRowGroups(String)}, {@link #parseChunks(String)}, and
   * {@link #parseTables(String)} separately, which each open their own reader (5-7 file opens
   * total).
   *
   * @param filePath path to the TsFile
   * @return AllMetadata containing all parsed metadata
   * @throws IOException if the file cannot be read
   */
  public AllMetadata parseAll(String filePath) throws IOException {
    logger.debug("Parsing all metadata from TsFile in single pass: {}", filePath);
    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
      ParsedMetadata parsed = parseMetadataInternal(reader);
      BasicMetadata basic =
          new BasicMetadata(
              parsed.getVersion(),
              parsed.getStartTime(),
              parsed.getEndTime(),
              parsed.getDeviceCount(),
              parsed.getMeasurementCount(),
              parsed.getRowGroupCount(),
              parsed.getChunkCount());

      List<MeasurementDTO> measurements =
          parseMeasurements(reader).stream()
              .map(
                  info ->
                      MeasurementDTO.builder()
                          .name(info.getName())
                          .dataType(info.getDataType())
                          .encoding(info.getEncoding())
                          .compression(info.getCompression())
                          .columnCategory(info.getColumnCategory())
                          .build())
              .toList();

      List<RowGroupDTO> rowGroups =
          parseRowGroups(reader).stream()
              .map(
                  info ->
                      RowGroupDTO.builder()
                          .index(info.getIndex())
                          .device(info.getDevice())
                          .startTime(info.getStartTime())
                          .endTime(info.getEndTime())
                          .chunkCount(info.getChunkCount())
                          .build())
              .toList();

      List<ChunkDTO> chunks =
          parseChunks(reader).stream()
              .map(
                  info ->
                      ChunkDTO.builder()
                          .measurement(info.getMeasurement())
                          .offset(info.getOffset())
                          .size(info.getSize())
                          .compressionRatio(info.getCompressionRatio())
                          .device(info.getDevice())
                          .dataType(info.getDataType())
                          .encoding(info.getEncoding())
                          .compression(info.getCompression())
                          .startTime(info.getStartTime())
                          .endTime(info.getEndTime())
                          .numOfPoints(info.getNumOfPoints())
                          .minValue(info.getMinValue())
                          .maxValue(info.getMaxValue())
                          .build())
              .toList();

      List<org.apache.tsfile.viewer.dto.TableDTO> tables =
          parseTables(reader).stream()
              .map(
                  info -> {
                    List<MeasurementDTO> tagDTOs =
                        info.getTagColumns().stream()
                            .map(
                                col ->
                                    MeasurementDTO.builder()
                                        .name(col.getName())
                                        .dataType(col.getDataType())
                                        .encoding(col.getEncoding())
                                        .compression(col.getCompression())
                                        .columnCategory(col.getColumnCategory())
                                        .build())
                            .toList();
                    List<MeasurementDTO> fieldDTOs =
                        info.getFieldColumns().stream()
                            .map(
                                col ->
                                    MeasurementDTO.builder()
                                        .name(col.getName())
                                        .dataType(col.getDataType())
                                        .encoding(col.getEncoding())
                                        .compression(col.getCompression())
                                        .columnCategory(col.getColumnCategory())
                                        .build())
                            .toList();
                    return org.apache.tsfile.viewer.dto.TableDTO.builder()
                        .tableName(info.getTableName())
                        .tagColumns(tagDTOs)
                        .fieldColumns(fieldDTOs)
                        .totalColumns(info.getTotalColumns())
                        .build();
                  })
              .toList();

      return new AllMetadata(basic, measurements, rowGroups, chunks, tables);
    }
  }

  // ============== DTO Conversion Methods ==============

  /** Record containing basic TsFile metadata. */
  public record BasicMetadata(
      String version,
      long startTime,
      long endTime,
      int deviceCount,
      int measurementCount,
      int rowGroupCount,
      int chunkCount) {}

  /**
   * Parses basic metadata from a TsFile path string.
   *
   * @param filePath path to the TsFile as a string
   * @return BasicMetadata record containing the extracted metadata
   * @throws IOException if the file cannot be read
   */
  public BasicMetadata parseMetadata(String filePath) throws IOException {
    return parseMetadata(Paths.get(filePath));
  }

  /**
   * Parses measurements from a TsFile path string and returns DTOs.
   *
   * @param filePath path to the TsFile as a string
   * @return list of MeasurementDTO containing measurement schema details
   * @throws IOException if the file cannot be read
   */
  public List<MeasurementDTO> parseMeasurements(String filePath) throws IOException {
    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
      List<MeasurementInfo> infos = parseMeasurements(reader);
      return infos.stream()
          .map(
              info ->
                  MeasurementDTO.builder()
                      .name(info.getName())
                      .dataType(info.getDataType())
                      .encoding(info.getEncoding())
                      .compression(info.getCompression())
                      .columnCategory(info.getColumnCategory())
                      .build())
          .toList();
    }
  }

  /**
   * Parses table schemas from a TsFile path string and returns DTOs.
   *
   * @param filePath path to the TsFile as a string
   * @return list of TableDTO containing table schema details
   * @throws IOException if the file cannot be read
   */
  public List<org.apache.tsfile.viewer.dto.TableDTO> parseTables(String filePath)
      throws IOException {
    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
      List<TableInfo> infos = parseTables(reader);
      return infos.stream()
          .map(
              info -> {
                // Convert tag columns
                List<MeasurementDTO> tagDTOs =
                    info.getTagColumns().stream()
                        .map(
                            col ->
                                MeasurementDTO.builder()
                                    .name(col.getName())
                                    .dataType(col.getDataType())
                                    .encoding(col.getEncoding())
                                    .compression(col.getCompression())
                                    .columnCategory(col.getColumnCategory())
                                    .build())
                        .toList();

                // Convert field columns
                List<MeasurementDTO> fieldDTOs =
                    info.getFieldColumns().stream()
                        .map(
                            col ->
                                MeasurementDTO.builder()
                                    .name(col.getName())
                                    .dataType(col.getDataType())
                                    .encoding(col.getEncoding())
                                    .compression(col.getCompression())
                                    .columnCategory(col.getColumnCategory())
                                    .build())
                        .toList();

                return org.apache.tsfile.viewer.dto.TableDTO.builder()
                    .tableName(info.getTableName())
                    .tagColumns(tagDTOs)
                    .fieldColumns(fieldDTOs)
                    .totalColumns(info.getTotalColumns())
                    .build();
              })
          .toList();
    }
  }

  /**
   * Parses RowGroups from a TsFile path string and returns DTOs.
   *
   * @param filePath path to the TsFile as a string
   * @return list of RowGroupDTO containing RowGroup metadata
   * @throws IOException if the file cannot be read
   */
  public List<RowGroupDTO> parseRowGroups(String filePath) throws IOException {
    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
      List<RowGroupInfo> infos = parseRowGroups(reader);
      return infos.stream()
          .map(
              info ->
                  RowGroupDTO.builder()
                      .index(info.getIndex())
                      .device(info.getDevice())
                      .startTime(info.getStartTime())
                      .endTime(info.getEndTime())
                      .chunkCount(info.getChunkCount())
                      .build())
          .toList();
    }
  }

  /**
   * Parses Chunks from a TsFile path string and returns DTOs.
   *
   * @param filePath path to the TsFile as a string
   * @return list of ChunkDTO containing Chunk metadata
   * @throws IOException if the file cannot be read
   */
  public List<ChunkDTO> parseChunks(String filePath) throws IOException {
    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
      List<ChunkInfo> infos = parseChunks(reader);
      return infos.stream()
          .map(
              info ->
                  ChunkDTO.builder()
                      .measurement(info.getMeasurement())
                      .offset(info.getOffset())
                      .size(info.getSize())
                      .compressionRatio(info.getCompressionRatio())
                      .device(info.getDevice())
                      .dataType(info.getDataType())
                      .encoding(info.getEncoding())
                      .compression(info.getCompression())
                      .startTime(info.getStartTime())
                      .endTime(info.getEndTime())
                      .numOfPoints(info.getNumOfPoints())
                      .minValue(info.getMinValue())
                      .maxValue(info.getMaxValue())
                      .build())
          .toList();
    }
  }
}
