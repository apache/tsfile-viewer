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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
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
    byte versionNumber = reader.readVersionNumber();
    ScanResult scan = scan(reader, List.of(), false);
    String version = "V" + versionNumber;

    logger.debug(
        "Parsed metadata: version={}, devices={}, measurements={}, rowGroups={}, chunks={}",
        version,
        scan.devices().size(),
        scan.measurementCount(),
        scan.devices().size(),
        totalChunks(scan));

    return new ParsedMetadata(
        version,
        scan.startTime(),
        scan.endTime(),
        scan.devices().size(),
        scan.measurementCount(),
        scan.devices().size(),
        totalChunks(scan));
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
    ScanResult scan = scan(reader, readTableSchemas(reader), true);
    logger.debug("Parsed {} unique measurements", scan.measurements().size());
    return scan.measurements();
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
    List<TableInfo> tableInfos = toTableInfos(readTableSchemas(reader));
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
    List<IDeviceID> devices = reader.getAllDevices();
    List<RowGroupInfo> rowGroups = new ArrayList<>(devices.size());
    for (int i = 0; i < devices.size(); i++) {
      rowGroups.add(readRowGroupInfo(reader, devices.get(i), i));
    }
    logger.debug("Parsed {} RowGroups", rowGroups.size());
    return rowGroups;
  }

  /**
   * Reads the RowGroup metadata of a single device.
   *
   * @param reader TsFile reader
   * @param device device to read
   * @param index RowGroup index of that device
   * @return RowGroup metadata of that single device
   * @throws IOException if reading fails
   */
  private RowGroupInfo readRowGroupInfo(TsFileSequenceReader reader, IDeviceID device, int index)
      throws IOException {
    long minStartTime = Long.MAX_VALUE;
    long maxEndTime = Long.MIN_VALUE;
    int chunkCount = 0;

    List<TimeseriesMetadata> deviceMetadata = reader.getDeviceTimeseriesMetadata(device);
    if (deviceMetadata != null) {
      for (TimeseriesMetadata tsMetadata : deviceMetadata) {
        Statistics<? extends Serializable> stats = tsMetadata.getStatistics();
        if (stats != null) {
          minStartTime = Math.min(minStartTime, stats.getStartTime());
          maxEndTime = Math.max(maxEndTime, stats.getEndTime());
        }

        chunkCount += countChunks(tsMetadata.getChunkMetadataList());
      }
    }

    if (minStartTime == Long.MAX_VALUE) {
      minStartTime = 0;
    }
    if (maxEndTime == Long.MIN_VALUE) {
      maxEndTime = 0;
    }

    return new RowGroupInfo(index, device.toString(), minStartTime, maxEndTime, chunkCount);
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
    for (IDeviceID device : reader.getAllDevices()) {
      chunks.addAll(readChunkInfos(reader, device));
    }
    logger.debug("Parsed {} Chunks", chunks.size());
    return chunks;
  }

  /**
   * Reads the chunk metadata of a single device.
   *
   * <p>Bounded by one device instead of the whole file, which is what keeps a chunk page
   * incremental.
   *
   * @param reader TsFile reader
   * @param device device to read
   * @return chunk metadata of that single device, in file order
   * @throws IOException if reading fails
   */
  private List<ChunkInfo> readChunkInfos(TsFileSequenceReader reader, IDeviceID device)
      throws IOException {
    List<ChunkInfo> chunks = new ArrayList<>();
    List<TimeseriesMetadata> deviceMetadata = reader.getDeviceTimeseriesMetadata(device);
    if (deviceMetadata == null) {
      return chunks;
    }

    for (TimeseriesMetadata tsMetadata : deviceMetadata) {
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

        String dataType =
            chunkMeta.getDataType() != null ? chunkMeta.getDataType().toString() : "UNKNOWN";

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

        double compressionRatio = calculateCompressionRatio(chunkMeta, numOfPoints);
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

  // ============== Incremental Summary & Paging ==============

  /** Summary metadata of a TsFile plus the device index used by the paging endpoints. */
  public record SummaryMetadata(
      BasicMetadata basic,
      List<MeasurementDTO> measurements,
      List<org.apache.tsfile.viewer.dto.TableDTO> tables,
      DeviceIndex deviceIndex) {}

  /**
   * Parses summary metadata from a TsFile in one streaming pass.
   *
   * <p>Never materializes the file's whole timeseries metadata: each device's metadata is read,
   * folded into counters, and dropped before the next device is visited.
   *
   * @param filePath path to the TsFile
   * @return summary metadata plus the device index of the same file version
   * @throws IOException if the file cannot be read
   */
  public SummaryMetadata parseSummary(String filePath) throws IOException {
    logger.debug("Parsing summary metadata from TsFile: {}", filePath);
    Path path = Paths.get(filePath);
    long lastModified = Files.getLastModifiedTime(path).toMillis();
    long size = Files.size(path);

    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
      String version = "V" + reader.readVersionNumber();
      List<TableSchema> schemas = readTableSchemas(reader);
      ScanResult scan = scan(reader, schemas, true);

      BasicMetadata basic =
          new BasicMetadata(
              version,
              scan.startTime(),
              scan.endTime(),
              scan.devices().size(),
              scan.measurementCount(),
              scan.devices().size(),
              totalChunks(scan));

      List<MeasurementDTO> measurements =
          scan.measurements().stream().map(TsFileParser::toMeasurementDTO).toList();
      List<org.apache.tsfile.viewer.dto.TableDTO> tables =
          toTableInfos(schemas).stream().map(TsFileParser::toTableDTO).toList();

      return new SummaryMetadata(
          basic,
          measurements,
          tables,
          DeviceIndex.of(lastModified, size, scan.devices(), scan.chunkCounts()));
    }
  }

  /**
   * Builds the device index of a TsFile without parsing measurements.
   *
   * @param filePath path to the TsFile
   * @return device index stamped with the file version it was read from
   * @throws IOException if the file cannot be read
   */
  public DeviceIndex parseDeviceIndex(String filePath) throws IOException {
    Path path = Paths.get(filePath);
    long lastModified = Files.getLastModifiedTime(path).toMillis();
    long size = Files.size(path);

    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
      ScanResult scan = scan(reader, List.of(), false);
      return DeviceIndex.of(lastModified, size, scan.devices(), scan.chunkCounts());
    }
  }

  /**
   * Reads the measurements carried by one device.
   *
   * <p>Tree model files nest measurements under devices, so the measurement choices a client
   * offers have to come from the selected device rather than from the whole file. Only that
   * device's metadata is read.
   *
   * @param reader TsFile reader
   * @param device device to read
   * @return measurements of that device, sorted by name, excluding the VECTOR time column
   * @throws IOException if reading fails
   */
  public List<MeasurementDTO> readDeviceMeasurements(
      TsFileSequenceReader reader, IDeviceID device) throws IOException {
    Map<String, List<ChunkMetadata>> seriesMetadata = reader.readChunkMetadataInDevice(device);
    List<MeasurementDTO> measurements = new ArrayList<>();
    for (Map.Entry<String, List<ChunkMetadata>> entry : seriesMetadata.entrySet()) {
      List<ChunkMetadata> chunkMetadata = entry.getValue();
      if (chunkMetadata == null || chunkMetadata.isEmpty()) {
        continue;
      }
      TSDataType dataType = chunkMetadata.get(0).getDataType();
      // VECTOR is the aligned time column, not a user measurement (same rule as the read path)
      if (dataType == TSDataType.VECTOR) {
        continue;
      }
      measurements.add(
          MeasurementDTO.builder().name(entry.getKey()).dataType(dataType.toString()).build());
    }
    measurements.sort(Comparator.comparing(MeasurementDTO::getName));
    return measurements;
  }

  /**
   * Reads one page of RowGroups.
   *
   * <p>Only the devices inside the page window are read, so a page's memory is bounded by the page
   * itself instead of the file's device count.
   *
   * @param reader TsFile reader
   * @param deviceIndex index of the same file version
   * @param offset absolute index of the first RowGroup to return
   * @param limit maximum number of RowGroups to return
   * @return RowGroups in file order; empty when the offset is past the last RowGroup
   * @throws IOException if reading fails
   */
  public List<RowGroupDTO> readRowGroupPage(
      TsFileSequenceReader reader, DeviceIndex deviceIndex, int offset, int limit)
      throws IOException {
    int total = deviceIndex.deviceCount();
    if (limit <= 0 || offset < 0 || offset >= total) {
      return List.of();
    }

    int end = Math.min(total, offset + limit);
    List<RowGroupDTO> page = new ArrayList<>(end - offset);
    for (int index = offset; index < end; index++) {
      page.add(toRowGroupDTO(readRowGroupInfo(reader, deviceIndex.devices().get(index), index)));
    }
    return page;
  }

  /**
   * Reads one page of Chunks, stitching devices together when the page crosses a device boundary.
   *
   * <p>Starts at the device that owns {@code offset} and stops once the page is full, so only the
   * devices the page touches are ever read.
   *
   * @param reader TsFile reader
   * @param deviceIndex index of the same file version
   * @param offset absolute index of the first chunk to return
   * @param limit maximum number of chunks to return
   * @return chunks in file order; empty when the offset is past the last chunk
   * @throws IOException if reading fails
   */
  public List<ChunkDTO> readChunkPage(
      TsFileSequenceReader reader, DeviceIndex deviceIndex, int offset, int limit)
      throws IOException {
    int total = deviceIndex.chunkCount();
    if (limit <= 0 || offset < 0 || offset >= total) {
      return List.of();
    }

    int end = Math.min(total, offset + limit);
    List<ChunkDTO> page = new ArrayList<>(end - offset);

    for (int position = deviceIndex.deviceIndexForChunk(offset);
        position < deviceIndex.deviceCount() && page.size() < end - offset;
        position++) {
      int base = deviceIndex.chunksBeforeDevice(position);
      List<ChunkInfo> deviceChunks = readChunkInfos(reader, deviceIndex.devices().get(position));

      for (int i = 0; i < deviceChunks.size(); i++) {
        int absolute = base + i;
        if (absolute < offset) {
          continue;
        }
        if (absolute >= end) {
          break;
        }
        page.add(toChunkDTO(deviceChunks.get(i)));
      }
    }
    return page;
  }

  /** Counters of one streaming pass; no chunk or timeseries metadata outlives the scan. */
  private record ScanResult(
      List<IDeviceID> devices,
      int[] chunkCounts,
      long startTime,
      long endTime,
      int measurementCount,
      List<MeasurementInfo> measurements) {}

  /**
   * Streams the file one device at a time and accumulates the summary counters.
   *
   * @param reader TsFile reader
   * @param schemas V4 table schemas, already read, used for TAG columns and encoding/compression
   * @param includeMeasurements whether measurement schema details are needed
   * @return counters of the file plus its device list and per-device chunk counts
   * @throws IOException if reading fails
   */
  private ScanResult scan(
      TsFileSequenceReader reader, List<TableSchema> schemas, boolean includeMeasurements)
      throws IOException {
    List<IDeviceID> devices = reader.getAllDevices();
    int[] chunkCounts = new int[devices.size()];

    Map<String, ColumnCategory> columnCategories = new HashMap<>();
    Map<String, IMeasurementSchema> columnSchemas = new HashMap<>();
    Map<String, MeasurementInfo> measurementMap = new LinkedHashMap<>();
    Set<String> measurementNames = new HashSet<>();
    if (includeMeasurements) {
      collectSchemaColumns(schemas, columnCategories, columnSchemas, measurementMap);
    }

    long minStartTime = Long.MAX_VALUE;
    long maxEndTime = Long.MIN_VALUE;

    for (int devicePosition = 0; devicePosition < devices.size(); devicePosition++) {
      List<TimeseriesMetadata> deviceMetadata =
          reader.getDeviceTimeseriesMetadata(devices.get(devicePosition));
      int deviceChunks = 0;

      if (deviceMetadata != null) {
        for (TimeseriesMetadata tsMetadata : deviceMetadata) {
          Statistics<? extends Serializable> stats = tsMetadata.getStatistics();
          if (stats != null) {
            minStartTime = Math.min(minStartTime, stats.getStartTime());
            maxEndTime = Math.max(maxEndTime, stats.getEndTime());
          }

          deviceChunks += countChunks(tsMetadata.getChunkMetadataList());

          String measurementId = tsMetadata.getMeasurementId();
          measurementNames.add(measurementId);
          if (includeMeasurements && !measurementMap.containsKey(measurementId)) {
            measurementMap.put(
                measurementId,
                toMeasurementInfo(reader, tsMetadata, columnCategories, columnSchemas));
          }
        }
      }
      chunkCounts[devicePosition] = deviceChunks;
    }

    if (minStartTime == Long.MAX_VALUE) {
      minStartTime = 0;
    }
    if (maxEndTime == Long.MIN_VALUE) {
      maxEndTime = 0;
    }

    return new ScanResult(
        devices,
        chunkCounts,
        minStartTime,
        maxEndTime,
        measurementNames.size(),
        includeMeasurements ? new ArrayList<>(measurementMap.values()) : List.of());
  }

  /**
   * Counts the chunks that the paging endpoints address.
   *
   * <p>Aligned files wrap some entries in non-{@link ChunkMetadata} objects that the chunk listing
   * skips; counting them here keeps the reported total equal to the number of addressable chunks.
   *
   * @param chunkMetadataList chunk metadata of one timeseries, may be null
   * @return number of addressable chunks
   */
  private static int countChunks(List<IChunkMetadata> chunkMetadataList) {
    if (chunkMetadataList == null) {
      return 0;
    }
    int count = 0;
    for (IChunkMetadata chunkMetadata : chunkMetadataList) {
      if (chunkMetadata instanceof ChunkMetadata) {
        count++;
      }
    }
    return count;
  }

  private static int totalChunks(ScanResult scan) {
    int total = 0;
    for (int count : scan.chunkCounts()) {
      total += count;
    }
    return total;
  }

  /**
   * Reads the V4 table schemas, returning an empty list for tree model files.
   *
   * @param reader TsFile reader
   * @return table schemas of the file
   */
  private List<TableSchema> readTableSchemas(TsFileSequenceReader reader) {
    try {
      Path path = Paths.get(reader.getFileName());
      try (ITsFileReader v4Reader = new TsFileReaderBuilder().file(path.toFile()).build()) {
        List<TableSchema> schemas = v4Reader.getAllTableSchema();
        return schemas == null ? List.of() : schemas;
      }
    } catch (Exception e) {
      logger.debug("Not a V4 table model file or failed to read TableSchema: {}", e.getMessage());
      return List.of();
    }
  }

  private void collectSchemaColumns(
      List<TableSchema> schemas,
      Map<String, ColumnCategory> columnCategories,
      Map<String, IMeasurementSchema> columnSchemas,
      Map<String, MeasurementInfo> measurementMap) {
    for (TableSchema schema : schemas) {
      List<IMeasurementSchema> schemaList = schema.getColumnSchemas();
      List<ColumnCategory> categories = schema.getColumnTypes();
      if (schemaList == null || categories == null) {
        continue;
      }
      for (int i = 0; i < schemaList.size() && i < categories.size(); i++) {
        IMeasurementSchema columnSchema = schemaList.get(i);
        ColumnCategory category = categories.get(i);
        if (category == null) {
          continue;
        }
        String columnName = columnSchema.getMeasurementName();
        columnCategories.put(columnName, category);
        columnSchemas.put(columnName, columnSchema);

        if (category == ColumnCategory.TAG) {
          measurementMap.put(
              columnName,
              new MeasurementInfo(
                  columnName,
                  schemaDataType(columnSchema, "STRING"),
                  schemaEncoding(columnSchema),
                  schemaCompression(columnSchema),
                  "TAG"));
        }
      }
    }
  }

  private MeasurementInfo toMeasurementInfo(
      TsFileSequenceReader reader,
      TimeseriesMetadata tsMetadata,
      Map<String, ColumnCategory> columnCategories,
      Map<String, IMeasurementSchema> columnSchemas) {
    String measurementId = tsMetadata.getMeasurementId();
    String dataType = tsMetadata.getTsDataType().toString();
    String encoding = "UNKNOWN";
    String compression = "UNKNOWN";

    IMeasurementSchema tableSchema = columnSchemas.get(measurementId);
    if (tableSchema != null) {
      if (tableSchema.getEncodingType() != null) {
        encoding = tableSchema.getEncodingType().toString();
      }
      if (tableSchema.getCompressor() != null) {
        compression = tableSchema.getCompressor().toString();
      }
    } else {
      List<IChunkMetadata> chunkMetadataList = tsMetadata.getChunkMetadataList();
      if (chunkMetadataList != null && !chunkMetadataList.isEmpty()) {
        IChunkMetadata firstChunk = chunkMetadataList.get(0);
        if (firstChunk instanceof ChunkMetadata chunkMeta) {
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
                "Could not read ChunkHeader for measurement {}: {}", measurementId, e.getMessage());
          }
        }
      }
    }

    ColumnCategory category = columnCategories.get(measurementId);
    return new MeasurementInfo(
        measurementId,
        dataType,
        encoding,
        compression,
        category != null ? category.toString() : "FIELD");
  }

  private List<TableInfo> toTableInfos(List<TableSchema> schemas) {
    List<TableInfo> tableInfos = new ArrayList<>();
    for (TableSchema schema : schemas) {
      List<MeasurementInfo> tagColumns = new ArrayList<>();
      List<MeasurementInfo> fieldColumns = new ArrayList<>();

      List<IMeasurementSchema> columnSchemas = schema.getColumnSchemas();
      List<ColumnCategory> categories = schema.getColumnTypes();
      if (columnSchemas != null && categories != null) {
        for (int i = 0; i < columnSchemas.size() && i < categories.size(); i++) {
          IMeasurementSchema columnSchema = columnSchemas.get(i);
          ColumnCategory category = categories.get(i);
          MeasurementInfo columnInfo =
              new MeasurementInfo(
                  columnSchema.getMeasurementName(),
                  schemaDataType(columnSchema, "UNKNOWN"),
                  schemaEncoding(columnSchema),
                  schemaCompression(columnSchema),
                  category.toString());

          if (category == ColumnCategory.TAG) {
            tagColumns.add(columnInfo);
          } else if (category == ColumnCategory.FIELD) {
            fieldColumns.add(columnInfo);
          }
        }
      }
      tableInfos.add(new TableInfo(schema.getTableName(), tagColumns, fieldColumns));
    }
    return tableInfos;
  }

  private static String schemaDataType(IMeasurementSchema schema, String defaultValue) {
    return schema.getType() != null ? schema.getType().toString() : defaultValue;
  }

  private static String schemaEncoding(IMeasurementSchema schema) {
    return schema.getEncodingType() != null ? schema.getEncodingType().toString() : "PLAIN";
  }

  private static String schemaCompression(IMeasurementSchema schema) {
    return schema.getCompressor() != null ? schema.getCompressor().toString() : "UNCOMPRESSED";
  }

  private static MeasurementDTO toMeasurementDTO(MeasurementInfo info) {
    return MeasurementDTO.builder()
        .name(info.getName())
        .dataType(info.getDataType())
        .encoding(info.getEncoding())
        .compression(info.getCompression())
        .columnCategory(info.getColumnCategory())
        .build();
  }

  private static org.apache.tsfile.viewer.dto.TableDTO toTableDTO(TableInfo info) {
    List<MeasurementDTO> tagDTOs =
        info.getTagColumns().stream().map(TsFileParser::toMeasurementDTO).toList();
    List<MeasurementDTO> fieldDTOs =
        info.getFieldColumns().stream().map(TsFileParser::toMeasurementDTO).toList();
    return org.apache.tsfile.viewer.dto.TableDTO.builder()
        .tableName(info.getTableName())
        .tagColumns(tagDTOs)
        .fieldColumns(fieldDTOs)
        .totalColumns(info.getTotalColumns())
        .build();
  }

  private static RowGroupDTO toRowGroupDTO(RowGroupInfo info) {
    return RowGroupDTO.builder()
        .index(info.getIndex())
        .device(info.getDevice())
        .startTime(info.getStartTime())
        .endTime(info.getEndTime())
        .chunkCount(info.getChunkCount())
        .build();
  }

  private static ChunkDTO toChunkDTO(ChunkInfo info) {
    return ChunkDTO.builder()
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
        .build();
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
