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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tsfile.file.metadata.IDeviceID;
import org.apache.tsfile.read.TsFileSequenceReader;
import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.dto.ChunkDTO;
import org.apache.tsfile.viewer.dto.MeasurementDTO;
import org.apache.tsfile.viewer.dto.PageDTO;
import org.apache.tsfile.viewer.dto.RowGroupDTO;
import org.apache.tsfile.viewer.dto.TimeRange;
import org.apache.tsfile.viewer.dto.TsFileMetadataDTO;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.EmptyFileException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.tsfile.DeviceIndex;
import org.apache.tsfile.viewer.tsfile.TsFileParser;

import jakarta.annotation.PostConstruct;

/**
 * Service for TsFile metadata operations.
 *
 * <p>Provides functionality for:
 *
 * <ul>
 *   <li>Parsing and caching the TsFile metadata summary (counts and schemas only)
 *   <li>Serving RowGroup and Chunk detail pages from incremental per-device reads
 *   <li>Caching a compact device index keyed by the file's mtime and size
 * </ul>
 *
 * <p>Validates: Requirement 2.1-2.5, 5.1 (Metadata extraction, caching)
 */
@Service
public class MetadataService {

  private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);

  /**
   * Device indexes are small (a device name list plus one int per device) but each one pins data
   * for a whole file, so this cache stays deliberately tiny instead of tracking file count.
   */
  private static final int DEVICE_INDEX_CACHE_SIZE = 8;

  private final TsFileProperties tsFileProperties;
  private final FileService fileService;
  private final TsFileParser tsFileParser;

  /** Cached summaries only: they carry no RowGroup or Chunk lists, so entries stay small. */
  private Cache<String, TsFileMetadataDTO> metadataCache;

  /** Cached device indexes, validated against the file's mtime and size on every use. */
  private Cache<String, DeviceIndex> deviceIndexCache;

  public MetadataService(
      TsFileProperties tsFileProperties, FileService fileService, TsFileParser tsFileParser) {
    this.tsFileProperties = tsFileProperties;
    this.fileService = fileService;
    this.tsFileParser = tsFileParser;
  }

  /** Initializes the metadata cache with configured settings. */
  @PostConstruct
  public void init() {
    int maxSize = tsFileProperties.getCache().getMetadata().getMaxSize();
    int ttlMinutes = tsFileProperties.getCache().getMetadata().getTtlMinutes();

    logger.info(
        "Initializing MetadataService cache with maxSize={}, ttlMinutes={}, deviceIndexMaxSize={}",
        maxSize,
        ttlMinutes,
        DEVICE_INDEX_CACHE_SIZE);

    this.metadataCache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
            .recordStats()
            .build();
    this.deviceIndexCache =
        Caffeine.newBuilder()
            .maximumSize(DEVICE_INDEX_CACHE_SIZE)
            .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
            .build();
  }

  /**
   * Gets the metadata summary for a TsFile by fileId.
   *
   * <p>The returned {@code rowGroups} and {@code chunks} lists are always empty: details are served
   * by {@link #getRowGroupsPage} and {@link #getChunksPage} instead, so building a summary never
   * materializes the whole file's chunk metadata.
   *
   * @param fileId the file identifier
   * @return TsFileMetadataDTO with summary metadata
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   * @throws IOException if the file cannot be read
   */
  public TsFileMetadataDTO getMetadata(String fileId)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting metadata for fileId={}", fileId);

    TsFileMetadataDTO cached = metadataCache.getIfPresent(fileId);
    if (cached != null) {
      logger.debug("Returning cached metadata for fileId={}", fileId);
      return cached;
    }

    String filePath = resolveFilePath(fileId);
    logger.debug("Parsing metadata summary for file: {}", filePath);

    TsFileParser.SummaryMetadata summary = tsFileParser.parseSummary(filePath);
    TsFileMetadataDTO metadata = toSummaryDTO(fileId, summary);

    metadataCache.put(fileId, metadata);
    deviceIndexCache.put(fileId, summary.deviceIndex());
    logger.debug("Cached metadata for fileId={}", fileId);

    return metadata;
  }

  /**
   * Gets one page of RowGroups (one RowGroup per device).
   *
   * <p>Only the devices inside the page window are read from the file.
   *
   * @param fileId the file identifier
   * @param offset zero-based offset of the first RowGroup
   * @param limit maximum number of RowGroups to return
   * @return page of RowGroups with the file's total RowGroup count
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   * @throws IOException if the file cannot be read
   */
  public PageDTO<RowGroupDTO> getRowGroupsPage(String fileId, int offset, int limit)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting RowGroup page for fileId={}, offset={}, limit={}", fileId, offset, limit);

    String filePath = resolveFilePath(fileId);
    DeviceIndex deviceIndex = getDeviceIndex(fileId, filePath);
    int total = deviceIndex.deviceCount();

    List<RowGroupDTO> items = List.of();
    if (offset < total && limit > 0) {
      try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
        items = tsFileParser.readRowGroupPage(reader, deviceIndex, offset, limit);
      }
    }

    return PageDTO.of(items, total, offset, limit);
  }

  /**
   * Gets the measurements carried by one device.
   *
   * <p>Only that device's metadata is read; the device is resolved through the cached device index,
   * so this does not walk the file.
   *
   * @param fileId the file identifier
   * @param deviceName device name as returned by the RowGroup paging endpoint
   * @return measurements of that device, empty when the device is not part of the file
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   * @throws IOException if the file cannot be read
   */
  public List<MeasurementDTO> getDeviceMeasurements(String fileId, String deviceName)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting measurements of device={} for fileId={}", deviceName, fileId);

    String filePath = resolveFilePath(fileId);
    DeviceIndex deviceIndex = getDeviceIndex(fileId, filePath);

    IDeviceID match = null;
    for (IDeviceID device : deviceIndex.devices()) {
      if (device.toString().equals(deviceName)) {
        match = device;
        break;
      }
    }
    if (match == null) {
      logger.debug("Device {} is not part of file {}", deviceName, filePath);
      return List.of();
    }

    try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
      return tsFileParser.readDeviceMeasurements(reader, match);
    }
  }

  /**
   * Gets one page of Chunks, crossing device boundaries when a page needs to.
   *
   * <p>Only the devices the page touches are read from the file.
   *
   * @param fileId the file identifier
   * @param offset zero-based offset of the first chunk
   * @param limit maximum number of chunks to return
   * @return page of Chunks with the file's total chunk count
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   * @throws IOException if the file cannot be read
   */
  public PageDTO<ChunkDTO> getChunksPage(String fileId, int offset, int limit)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting chunk page for fileId={}, offset={}, limit={}", fileId, offset, limit);

    String filePath = resolveFilePath(fileId);
    DeviceIndex deviceIndex = getDeviceIndex(fileId, filePath);
    int total = deviceIndex.chunkCount();

    List<ChunkDTO> items = List.of();
    if (offset < total && limit > 0) {
      try (TsFileSequenceReader reader = new TsFileSequenceReader(filePath)) {
        items = tsFileParser.readChunkPage(reader, deviceIndex, offset, limit);
      }
    }

    return PageDTO.of(items, total, offset, limit);
  }

  /**
   * Resolves a fileId to an existing, non-empty file path.
   *
   * @param fileId the file identifier
   * @return absolute file path
   */
  private String resolveFilePath(String fileId)
      throws TsFileNotFoundException, AccessDeniedException {
    String filePath = fileService.getFilePath(fileId);

    // Pre-check: reject empty files before attempting to parse
    try {
      if (Files.size(Paths.get(filePath)) == 0) {
        throw new EmptyFileException(
            "The file is empty (0 bytes). It may be a placeholder or incomplete write.");
      }
    } catch (IOException e) {
      logger.warn("Cannot determine file size for {}: {}", filePath, e.getMessage());
    }

    return filePath;
  }

  /**
   * Returns the cached device index of a file, rebuilding it when the file changed on disk.
   *
   * @param fileId the file identifier
   * @param filePath the resolved file path
   * @return device index of the file's current version
   */
  private DeviceIndex getDeviceIndex(String fileId, String filePath) throws IOException {
    DeviceIndex cached = deviceIndexCache.getIfPresent(fileId);
    if (cached != null && !isStale(cached, filePath)) {
      return cached;
    }

    DeviceIndex deviceIndex = tsFileParser.parseDeviceIndex(filePath);
    deviceIndexCache.put(fileId, deviceIndex);
    return deviceIndex;
  }

  /**
   * Tells whether a cached index no longer matches the file on disk.
   *
   * @param deviceIndex cached index
   * @param filePath file path
   * @return true when the file's mtime or size differs from the indexed version
   */
  private static boolean isStale(DeviceIndex deviceIndex, String filePath) {
    try {
      Path path = Paths.get(filePath);
      return !deviceIndex.matches(Files.getLastModifiedTime(path).toMillis(), Files.size(path));
    } catch (IOException e) {
      logger.debug("Cannot stat {}: {}", filePath, e.getMessage());
      return true;
    }
  }

  /**
   * Converts parsed summary metadata to the API DTO.
   *
   * @param fileId the file identifier
   * @param summary parsed summary metadata
   * @return DTO with empty RowGroup and Chunk lists
   */
  private TsFileMetadataDTO toSummaryDTO(String fileId, TsFileParser.SummaryMetadata summary) {
    TsFileParser.BasicMetadata basic = summary.basic();

    // Check if the file is empty (valid structure but no data)
    if (basic.deviceCount() == 0
        && summary.measurements().isEmpty()
        && summary.tables().isEmpty()
        && summary.deviceIndex().chunkCount() == 0) {
      throw new EmptyFileException(
          "The TsFile is valid but contains no data. It may be a newly created or empty file.");
    }

    return TsFileMetadataDTO.builder()
        .fileId(fileId)
        .version(basic.version())
        .timeRange(new TimeRange(basic.startTime(), basic.endTime()))
        .deviceCount(basic.deviceCount())
        .measurementCount(basic.measurementCount())
        .rowGroupCount(basic.rowGroupCount())
        .chunkCount(basic.chunkCount())
        .measurements(summary.measurements())
        .rowGroups(List.of())
        .chunks(List.of())
        .tables(summary.tables())
        .build();
  }

  /**
   * Invalidates cached metadata for a fileId.
   *
   * @param fileId the file identifier
   */
  public void invalidateCache(String fileId) {
    logger.debug("Invalidating cache for fileId={}", fileId);
    metadataCache.invalidate(fileId);
    deviceIndexCache.invalidate(fileId);
  }

  /** Invalidates all cached metadata. */
  public void invalidateAllCache() {
    logger.info("Invalidating all metadata cache");
    metadataCache.invalidateAll();
    deviceIndexCache.invalidateAll();
  }

  /**
   * Gets cache statistics for monitoring.
   *
   * @return cache statistics
   */
  public CacheStats getCacheStats() {
    var stats = metadataCache.stats();
    return new CacheStats(
        stats.hitCount(),
        stats.missCount(),
        stats.loadSuccessCount(),
        stats.loadFailureCount(),
        stats.evictionCount(),
        stats.hitRate(),
        metadataCache.estimatedSize());
  }

  /** Cache statistics record for monitoring. */
  public record CacheStats(
      long hitCount,
      long missCount,
      long loadSuccessCount,
      long loadFailureCount,
      long evictionCount,
      double hitRate,
      long estimatedSize) {}
}
