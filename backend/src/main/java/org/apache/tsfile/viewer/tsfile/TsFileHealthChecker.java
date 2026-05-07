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
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tsfile.common.conf.TSFileConfig;
import org.apache.tsfile.common.conf.TSFileDescriptor;
import org.apache.tsfile.common.constant.TsFileConstant;
import org.apache.tsfile.encoding.decoder.Decoder;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.file.MetaMarker;
import org.apache.tsfile.file.header.ChunkGroupHeader;
import org.apache.tsfile.file.header.ChunkHeader;
import org.apache.tsfile.file.header.PageHeader;
import org.apache.tsfile.file.metadata.IChunkMetadata;
import org.apache.tsfile.file.metadata.IDeviceID;
import org.apache.tsfile.file.metadata.TimeseriesMetadata;
import org.apache.tsfile.file.metadata.enums.TSEncoding;
import org.apache.tsfile.read.TsFileSequenceReader;
import org.apache.tsfile.read.common.BatchData;
import org.apache.tsfile.read.reader.page.PageReader;
import org.apache.tsfile.read.reader.page.TimePageReader;
import org.apache.tsfile.read.reader.page.ValuePageReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tsfile.viewer.dto.ErrorSeverity;
import org.apache.tsfile.viewer.dto.HealthStatus;
import org.apache.tsfile.viewer.dto.ScanErrorType;
import org.apache.tsfile.viewer.service.FileError;
import org.apache.tsfile.viewer.service.ScanResult;

/**
 * Core health-checking logic for TsFile files.
 *
 * <p>This is a pure Java class with <b>no Spring dependencies</b>. It creates independent {@link
 * TsFileSequenceReader} instances for each check and does not interact with the existing {@link
 * TsFileReaderCache}.
 *
 * <p>Follows the same validation approach as IoTDB's {@code
 * TsFileResourceUtils.validateTsFileDataCorrectness}: marker-based sequential traversal with
 * page-level data validation (timestamp ordering, page overlap, header consistency).
 *
 * <p>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 7.4
 */
public class TsFileHealthChecker {

  private static final Logger logger = LoggerFactory.getLogger(TsFileHealthChecker.class);

  /**
   * Performs a full health check on a single TsFile following IoTDB's validation approach.
   *
   * <p>Executes the following steps in order:
   *
   * <ol>
   *   <li>Validate file completeness (isComplete)
   *   <li>Build chunk metadata offset map for cross-referencing
   *   <li>Sequential marker-based traversal validating:
   *       <ul>
   *         <li>Chunk header offset matches metadata
   *         <li>Timestamps are strictly incremental within each page
   *         <li>No time range overlap between pages
   *         <li>Page header start/end times match actual data
   *         <li>ChunkGroupHeader device IDs are non-empty
   *       </ul>
   * </ol>
   *
   * @param filePath path to the TsFile to check
   * @return a {@link ScanResult} containing the health status and any errors found
   */
  public ScanResult check(Path filePath) {
    long startTime = System.currentTimeMillis();
    List<FileError> errors = new ArrayList<>();
    long fileSize = resolveFileSize(filePath);

    // Pre-check: file must exist and have minimum size for a valid TsFile header
    if (fileSize == 0) {
      errors.add(
          new FileError(
              ScanErrorType.FORMAT_INCOMPATIBLE,
              "File size",
              "File is empty (0 bytes)",
              ErrorSeverity.CRITICAL));
      long scanDurationMs = System.currentTimeMillis() - startTime;
      return new ScanResult(
          filePath.toString(), fileSize, HealthStatus.ERROR, errors, scanDurationMs);
    }

    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath.toString())) {
      // Step 1: Check file completeness
      if (!reader.isComplete()) {
        errors.add(
            new FileError(
                ScanErrorType.FORMAT_INCOMPATIBLE,
                "File structure",
                "File is not complete (missing tail magic string or truncated)",
                ErrorSeverity.CRITICAL));
      } else {
        // Step 2: Build chunk metadata map
        Map<Long, IChunkMetadata> chunkMetadataMap = getChunkMetadataMap(reader);
        if (chunkMetadataMap.isEmpty()) {
          errors.add(
              new FileError(
                  ScanErrorType.STRUCTURE_CORRUPT,
                  "File content",
                  "No data found in the file (empty chunk metadata)",
                  ErrorSeverity.ERROR));
        } else {
          // Step 3: Marker-based traversal with data validation
          validateDataCorrectness(reader, chunkMetadataMap, errors);
        }
      }
    } catch (IOException e) {
      logger.warn("Failed to open TsFile for health check: {}", filePath, e);
      errors.add(
          new FileError(
              ScanErrorType.FORMAT_INCOMPATIBLE,
              "File access",
              "Cannot open file: " + e.getMessage(),
              ErrorSeverity.CRITICAL));
    } catch (Exception e) {
      logger.warn("Unexpected error during health check: {}", filePath, e);
      errors.add(
          new FileError(
              ScanErrorType.FORMAT_INCOMPATIBLE,
              "Health check",
              "Unexpected error (likely incompatible format): " + e.getMessage(),
              ErrorSeverity.CRITICAL));
    }

    long scanDurationMs = System.currentTimeMillis() - startTime;
    HealthStatus healthStatus = determineHealthStatus(errors);
    return new ScanResult(filePath.toString(), fileSize, healthStatus, errors, scanDurationMs);
  }

  /**
   * Performs a quick health check on a single TsFile.
   *
   * <p>Only checks file completeness and metadata availability without full data traversal.
   *
   * @param filePath path to the TsFile to check
   * @return a {@link ScanResult} containing the health status and any errors found
   */
  public ScanResult quickCheck(Path filePath) {
    long startTime = System.currentTimeMillis();
    List<FileError> errors = new ArrayList<>();
    long fileSize = resolveFileSize(filePath);

    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath.toString())) {
      if (!reader.isComplete()) {
        errors.add(
            new FileError(
                ScanErrorType.FORMAT_INCOMPATIBLE,
                "File structure",
                "File is not complete (missing tail magic string or truncated)",
                ErrorSeverity.CRITICAL));
      } else {
        Map<Long, IChunkMetadata> chunkMetadataMap = getChunkMetadataMap(reader);
        if (chunkMetadataMap.isEmpty()) {
          errors.add(
              new FileError(
                  ScanErrorType.STRUCTURE_CORRUPT,
                  "File content",
                  "No data found in the file (empty chunk metadata)",
                  ErrorSeverity.ERROR));
        }
      }
    } catch (Exception e) {
      logger.warn("Quick check failed: {}", filePath, e);
      errors.add(
          new FileError(
              ScanErrorType.FORMAT_INCOMPATIBLE,
              "File access",
              "Cannot open file: " + e.getMessage(),
              ErrorSeverity.CRITICAL));
    }

    long scanDurationMs = System.currentTimeMillis() - startTime;
    HealthStatus healthStatus = determineHealthStatus(errors);
    return new ScanResult(filePath.toString(), fileSize, healthStatus, errors, scanDurationMs);
  }

  // ---------------------------------------------------------------------------
  // Core validation: marker-based traversal (IoTDB approach)
  // ---------------------------------------------------------------------------

  // TODO: Implement CHUNK_STATISTICS_MISMATCH validation (compare Chunk-level statistics
  //  against aggregated Page-level data) and TIMESERIES_METADATA_MISMATCH validation
  //  (compare TimeseriesMetadata statistics against aggregated ChunkMetadata statistics).
  //  These error types are defined in ScanErrorType but not yet detected.

  /**
   * Validates data correctness by sequential marker-based traversal, following IoTDB's {@code
   * validateTsFileDataCorrectness} approach.
   */
  private void validateDataCorrectness(
      TsFileSequenceReader reader,
      Map<Long, IChunkMetadata> chunkMetadataMap,
      List<FileError> errors) {

    List<List<long[]>> alignedTimeBatches = new ArrayList<>();
    Map<String, Integer> valueColumn2TimeBatchIndex = new HashMap<>();

    try {
      reader.position((long) TSFileConfig.MAGIC_STRING.getBytes().length + 1);
      int pageIndex = 0;
      byte marker;

      while ((marker = reader.readMarker()) != MetaMarker.SEPARATOR) {
        switch (marker) {
          case MetaMarker.CHUNK_HEADER:
          case MetaMarker.TIME_CHUNK_HEADER:
          case MetaMarker.VALUE_CHUNK_HEADER:
          case MetaMarker.ONLY_ONE_PAGE_CHUNK_HEADER:
          case MetaMarker.ONLY_ONE_PAGE_TIME_CHUNK_HEADER:
          case MetaMarker.ONLY_ONE_PAGE_VALUE_CHUNK_HEADER:
            long chunkOffset = reader.position();
            ChunkHeader header = reader.readChunkHeader(marker);
            IChunkMetadata chunkMetadata = chunkMetadataMap.get(chunkOffset - Byte.BYTES);

            // Validate chunk offset matches metadata
            if (chunkMetadata != null
                && !chunkMetadata.getMeasurementUid().equals(header.getMeasurementID())) {
              errors.add(
                  new FileError(
                      ScanErrorType.STRUCTURE_CORRUPT,
                      "Chunk at offset " + (chunkOffset - Byte.BYTES),
                      "Chunk measurement ID mismatch: metadata says '"
                          + chunkMetadata.getMeasurementUid()
                          + "' but chunk header says '"
                          + header.getMeasurementID()
                          + "'",
                      ErrorSeverity.ERROR));
            }

            String measurement = header.getMeasurementID();
            List<long[]> alignedTimeBatch = null;
            if (header.getDataType() == TSDataType.VECTOR) {
              alignedTimeBatch = new ArrayList<>();
              alignedTimeBatches.add(alignedTimeBatch);
            } else if (marker == MetaMarker.ONLY_ONE_PAGE_VALUE_CHUNK_HEADER
                || marker == MetaMarker.VALUE_CHUNK_HEADER) {
              int timeBatchIndex = valueColumn2TimeBatchIndex.getOrDefault(measurement, 0);
              valueColumn2TimeBatchIndex.put(measurement, timeBatchIndex + 1);
              if (timeBatchIndex < alignedTimeBatches.size()) {
                alignedTimeBatch = alignedTimeBatches.get(timeBatchIndex);
              }
            }

            int dataSize = header.getDataSize();
            if (dataSize == 0) {
              break;
            }

            boolean isHasStatistic = (header.getChunkType() & 0x3F) == MetaMarker.CHUNK_HEADER;
            Decoder defaultTimeDecoder =
                Decoder.getDecoderByType(
                    TSEncoding.valueOf(TSFileDescriptor.getInstance().getConfig().getTimeEncoder()),
                    TSDataType.INT64);
            Decoder valueDecoder =
                Decoder.getDecoderByType(header.getEncodingType(), header.getDataType());

            pageIndex = 0;
            long lastNoAlignedPageEndTime = Long.MIN_VALUE;

            while (dataSize > 0) {
              valueDecoder.reset();
              PageHeader pageHeader = reader.readPageHeader(header.getDataType(), isHasStatistic);
              ByteBuffer pageData = reader.readPage(pageHeader, header.getCompressionType());

              if ((header.getChunkType() & TsFileConstant.TIME_COLUMN_MASK)
                  == TsFileConstant.TIME_COLUMN_MASK) {
                // Time Chunk
                TimePageReader timePageReader =
                    new TimePageReader(pageHeader, pageData, defaultTimeDecoder);
                long[] pageTimestamps = timePageReader.getNextTimeBatch();
                long pageHeaderStartTime =
                    isHasStatistic
                        ? pageHeader.getStartTime()
                        : (chunkMetadata != null ? chunkMetadata.getStartTime() : 0);
                long pageHeaderEndTime =
                    isHasStatistic
                        ? pageHeader.getEndTime()
                        : (chunkMetadata != null ? chunkMetadata.getEndTime() : 0);

                validateTimeFrame(
                    alignedTimeBatch,
                    pageTimestamps,
                    pageHeaderStartTime,
                    pageHeaderEndTime,
                    measurement,
                    errors);

                if (alignedTimeBatch != null) {
                  alignedTimeBatch.add(pageTimestamps);
                }
              } else if ((header.getChunkType() & TsFileConstant.VALUE_COLUMN_MASK)
                  == TsFileConstant.VALUE_COLUMN_MASK) {
                // Value Chunk — read value batch (validates decompression)
                try {
                  if (alignedTimeBatch != null && pageIndex < alignedTimeBatch.size()) {
                    ValuePageReader valuePageReader =
                        new ValuePageReader(
                            pageHeader, pageData, header.getDataType(), valueDecoder);
                    valuePageReader.nextValueBatch(alignedTimeBatch.get(pageIndex));
                  }
                } catch (Exception e) {
                  errors.add(
                      new FileError(
                          ScanErrorType.DATA_READ_ERROR,
                          "Value page for '" + measurement + "'",
                          "Failed to read value page: " + e.getMessage(),
                          ErrorSeverity.WARNING));
                }
              } else {
                // NonAligned Chunk
                try {
                  PageReader pageReader =
                      new PageReader(
                          pageData, header.getDataType(), valueDecoder, defaultTimeDecoder);
                  BatchData batchData = pageReader.getAllSatisfiedPageData();
                  long pageHeaderStartTime =
                      isHasStatistic
                          ? pageHeader.getStartTime()
                          : (chunkMetadata != null ? chunkMetadata.getStartTime() : 0);
                  long pageHeaderEndTime =
                      isHasStatistic
                          ? pageHeader.getEndTime()
                          : (chunkMetadata != null ? chunkMetadata.getEndTime() : 0);
                  long pageStartTime = Long.MAX_VALUE;
                  long previousTime = Long.MIN_VALUE;

                  while (batchData.hasCurrent()) {
                    long currentTime = batchData.currentTime();
                    if (lastNoAlignedPageEndTime != Long.MIN_VALUE
                        && currentTime <= lastNoAlignedPageEndTime) {
                      errors.add(
                          new FileError(
                              ScanErrorType.DATA_READ_ERROR,
                              "Page for '" + measurement + "'",
                              "Time ranges overlap between pages",
                              ErrorSeverity.ERROR));
                      break;
                    }
                    if (currentTime <= previousTime) {
                      errors.add(
                          new FileError(
                              ScanErrorType.DATA_READ_ERROR,
                              "Page for '" + measurement + "'",
                              "Timestamp is repeated or not incremental",
                              ErrorSeverity.ERROR));
                      break;
                    }
                    pageStartTime = Math.min(pageStartTime, currentTime);
                    previousTime = currentTime;
                    batchData.next();
                  }
                  if (previousTime != Long.MIN_VALUE) {
                    lastNoAlignedPageEndTime = previousTime;
                  }

                  if (pageStartTime != Long.MAX_VALUE && pageHeaderStartTime != pageStartTime) {
                    errors.add(
                        new FileError(
                            ScanErrorType.DATA_READ_ERROR,
                            "Page for '" + measurement + "'",
                            "Start time in page data ("
                                + pageStartTime
                                + ") differs from page header ("
                                + pageHeaderStartTime
                                + ")",
                            ErrorSeverity.ERROR));
                  }
                  if (previousTime != Long.MIN_VALUE && pageHeaderEndTime != previousTime) {
                    errors.add(
                        new FileError(
                            ScanErrorType.DATA_READ_ERROR,
                            "Page for '" + measurement + "'",
                            "End time in page data ("
                                + previousTime
                                + ") differs from page header ("
                                + pageHeaderEndTime
                                + ")",
                            ErrorSeverity.ERROR));
                  }
                } catch (Exception e) {
                  errors.add(
                      new FileError(
                          ScanErrorType.DATA_READ_ERROR,
                          "Page for '" + measurement + "'",
                          "Failed to read page data: " + e.getMessage(),
                          ErrorSeverity.WARNING));
                }
              }
              pageIndex++;
              dataSize -= pageHeader.getSerializedPageSize();
            }
            break;

          case MetaMarker.CHUNK_GROUP_HEADER:
            valueColumn2TimeBatchIndex.clear();
            alignedTimeBatches.clear();
            ChunkGroupHeader chunkGroupHeader = reader.readChunkGroupHeader();
            if (chunkGroupHeader.getDeviceID() == null
                || chunkGroupHeader.getDeviceID().isEmpty()) {
              errors.add(
                  new FileError(
                      ScanErrorType.STRUCTURE_CORRUPT,
                      "ChunkGroup header",
                      "Device ID is null or empty",
                      ErrorSeverity.ERROR));
            }
            break;

          case MetaMarker.OPERATION_INDEX_RANGE:
            reader.readPlanIndex();
            break;

          default:
            MetaMarker.handleUnexpectedMarker(marker);
        }
      }
    } catch (IOException
        | NegativeArraySizeException
        | IllegalArgumentException
        | ArrayIndexOutOfBoundsException e) {
      logger.warn("Data validation traversal failed: {}", e.getMessage());
      errors.add(
          new FileError(
              ScanErrorType.DATA_READ_ERROR,
              "Data traversal",
              "Data validation failed: " + e.getMessage(),
              ErrorSeverity.ERROR));
    }
  }

  // ---------------------------------------------------------------------------
  // Time frame validation (IoTDB approach)
  // ---------------------------------------------------------------------------

  /** Validates timestamp ordering and page overlap for aligned time chunks. */
  private void validateTimeFrame(
      List<long[]> timeBatch,
      long[] pageTimestamps,
      long pageHeaderStartTime,
      long pageHeaderEndTime,
      String measurement,
      List<FileError> errors) {

    if (pageTimestamps == null || pageTimestamps.length == 0) {
      return;
    }

    if (pageHeaderStartTime != pageTimestamps[0]) {
      errors.add(
          new FileError(
              ScanErrorType.DATA_READ_ERROR,
              "Time page for '" + measurement + "'",
              "Start time in page data ("
                  + pageTimestamps[0]
                  + ") differs from page header ("
                  + pageHeaderStartTime
                  + ")",
              ErrorSeverity.ERROR));
    }

    if (pageHeaderEndTime != pageTimestamps[pageTimestamps.length - 1]) {
      errors.add(
          new FileError(
              ScanErrorType.DATA_READ_ERROR,
              "Time page for '" + measurement + "'",
              "End time in page data ("
                  + pageTimestamps[pageTimestamps.length - 1]
                  + ") differs from page header ("
                  + pageHeaderEndTime
                  + ")",
              ErrorSeverity.ERROR));
    }

    for (int i = 0; i < pageTimestamps.length - 1; i++) {
      if (pageTimestamps[i + 1] <= pageTimestamps[i]) {
        errors.add(
            new FileError(
                ScanErrorType.DATA_READ_ERROR,
                "Time page for '" + measurement + "'",
                "Timestamp is repeated or not incremental at index " + i,
                ErrorSeverity.ERROR));
        break;
      }
    }

    if (timeBatch != null && !timeBatch.isEmpty()) {
      long[] lastPageTimes = timeBatch.get(timeBatch.size() - 1);
      if (lastPageTimes[lastPageTimes.length - 1] >= pageTimestamps[0]) {
        errors.add(
            new FileError(
                ScanErrorType.DATA_READ_ERROR,
                "Time page for '" + measurement + "'",
                "Time ranges overlap between pages",
                ErrorSeverity.ERROR));
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a map of chunk header offset to IChunkMetadata for cross-referencing during traversal.
   */
  private Map<Long, IChunkMetadata> getChunkMetadataMap(TsFileSequenceReader reader)
      throws IOException {
    Map<Long, IChunkMetadata> offset2ChunkMetadata = new HashMap<>();
    Map<IDeviceID, List<TimeseriesMetadata>> device2Metadata =
        reader.getAllTimeseriesMetadata(true);
    for (Map.Entry<IDeviceID, List<TimeseriesMetadata>> entry : device2Metadata.entrySet()) {
      for (TimeseriesMetadata timeseriesMetadata : entry.getValue()) {
        for (IChunkMetadata chunkMetadata : timeseriesMetadata.getChunkMetadataList()) {
          offset2ChunkMetadata.put(chunkMetadata.getOffsetOfChunkHeader(), chunkMetadata);
        }
      }
    }
    return offset2ChunkMetadata;
  }

  /**
   * Determines the overall health status based on the worst error severity found.
   *
   * <ul>
   *   <li>No errors → {@link HealthStatus#HEALTHY}
   *   <li>Only WARNING errors → {@link HealthStatus#WARNING}
   *   <li>Any ERROR or CRITICAL errors → {@link HealthStatus#ERROR}
   * </ul>
   */
  private HealthStatus determineHealthStatus(List<FileError> errors) {
    if (errors.isEmpty()) {
      return HealthStatus.HEALTHY;
    }

    boolean hasErrorOrCritical =
        errors.stream()
            .anyMatch(
                e ->
                    e.getSeverity() == ErrorSeverity.ERROR
                        || e.getSeverity() == ErrorSeverity.CRITICAL);

    return hasErrorOrCritical ? HealthStatus.ERROR : HealthStatus.WARNING;
  }

  /** Resolves the file size in bytes, returning 0 if the file does not exist or cannot be read. */
  private long resolveFileSize(Path filePath) {
    try {
      return Files.size(filePath);
    } catch (IOException e) {
      logger.debug("Cannot determine file size for {}: {}", filePath, e.getMessage());
      return 0;
    }
  }
}
