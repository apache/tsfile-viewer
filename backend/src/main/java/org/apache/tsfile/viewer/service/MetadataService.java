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
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.dto.TsFileMetadataDTO;
import org.apache.tsfile.viewer.dto.TimeRange;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.EmptyFileException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.tsfile.TsFileParser;

import jakarta.annotation.PostConstruct;

/**
 * Service for TsFile metadata operations.
 *
 * <p>Provides functionality for:
 *
 * <ul>
 *   <li>Parsing and caching TsFile metadata
 *   <li>Extracting measurements, RowGroups, and Chunks
 *   <li>Caching metadata by fileId with TTL
 * </ul>
 *
 * <p>Validates: Requirement 2.1-2.5, 5.1 (Metadata extraction, caching)
 */
@Service
public class MetadataService {

  private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);

  private final TsFileProperties tsFileProperties;
  private final FileService fileService;
  private final TsFileParser tsFileParser;

  private Cache<String, TsFileMetadataDTO> metadataCache;

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
        "Initializing MetadataService cache with maxSize={}, ttlMinutes={}", maxSize, ttlMinutes);

    this.metadataCache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
            .recordStats()
            .build();
  }

  /**
   * Gets metadata for a TsFile by fileId.
   *
   * <p>First checks the cache, then parses the file if not cached.
   *
   * @param fileId the file identifier
   * @return TsFileMetadataDTO with complete metadata
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   * @throws IOException if the file cannot be read
   */
  public TsFileMetadataDTO getMetadata(String fileId)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting metadata for fileId={}", fileId);

    // Check cache first
    TsFileMetadataDTO cached = metadataCache.getIfPresent(fileId);
    if (cached != null) {
      logger.debug("Returning cached metadata for fileId={}", fileId);
      return cached;
    }

    // Get file path and parse metadata
    String filePath = fileService.getFilePath(fileId);
    logger.debug("Parsing metadata for file: {}", filePath);

    // Pre-check: reject empty files before attempting to parse
    try {
      long fileSize = Files.size(Paths.get(filePath));
      if (fileSize == 0) {
        throw new EmptyFileException(
            "The file is empty (0 bytes). It may be a placeholder or incomplete write.");
      }
    } catch (IOException e) {
      logger.warn("Cannot determine file size for {}: {}", filePath, e.getMessage());
    }

    TsFileMetadataDTO metadata = parseMetadata(fileId, filePath);

    // Cache the result
    metadataCache.put(fileId, metadata);
    logger.debug("Cached metadata for fileId={}", fileId);

    return metadata;
  }

  /**
   * Parses metadata from a TsFile.
   *
   * @param fileId the file identifier
   * @param filePath the file path
   * @return TsFileMetadataDTO with parsed metadata
   * @throws IOException if the file cannot be read
   */
  private TsFileMetadataDTO parseMetadata(String fileId, String filePath) throws IOException {
    // Parse all metadata in a single pass (opens the file only once instead of 5-7 times)
    TsFileParser.AllMetadata all = tsFileParser.parseAll(filePath);

    // Check if the file is empty (valid structure but no data)
    if (all.basic().deviceCount() == 0
        && all.measurements().isEmpty()
        && all.rowGroups().isEmpty()
        && all.chunks().isEmpty()
        && all.tables().isEmpty()) {
      throw new EmptyFileException(
          "The TsFile is valid but contains no data. It may be a newly created or empty file.");
    }

    return TsFileMetadataDTO.builder()
        .fileId(fileId)
        .version(all.basic().version())
        .timeRange(new TimeRange(all.basic().startTime(), all.basic().endTime()))
        .deviceCount(all.basic().deviceCount())
        .measurementCount(all.basic().measurementCount())
        .rowGroupCount(all.rowGroups().size())
        .chunkCount(all.chunks().size())
        .measurements(all.measurements())
        .rowGroups(all.rowGroups())
        .chunks(all.chunks())
        .tables(all.tables())
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
  }

  /** Invalidates all cached metadata. */
  public void invalidateAllCache() {
    logger.info("Invalidating all metadata cache");
    metadataCache.invalidateAll();
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
