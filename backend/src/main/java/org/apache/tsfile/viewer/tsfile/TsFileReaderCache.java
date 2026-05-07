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
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.tsfile.read.TsFileSequenceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.apache.tsfile.viewer.config.TsFileProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Cache manager for TsFile readers using Caffeine cache.
 *
 * <p>Provides thread-safe caching of TsFileSequenceReader instances with LRU eviction policy,
 * configurable max size and TTL. Ensures proper resource cleanup when readers are evicted.
 *
 * <p><b>Thread-safety warning:</b> {@link TsFileSequenceReader} itself is <em>not</em> thread-safe.
 * Callers that share a cached reader across concurrent requests must provide their own
 * synchronization, or use this cache only for single-threaded access patterns (e.g. metadata
 * parsing where each request holds the reader exclusively).
 *
 * <p>Validates: Requirement 5.1, 5.2, 5.3, 5.6 (Caching, concurrency)
 */
@Component
public class TsFileReaderCache {

  private static final Logger logger = LoggerFactory.getLogger(TsFileReaderCache.class);

  private final TsFileProperties tsFileProperties;
  private Cache<String, CachedReader> readerCache;

  /**
   * Wrapper class for cached TsFileSequenceReader with associated file path.
   *
   * <p>Stores the reader along with its file path for logging and debugging purposes.
   */
  public static class CachedReader {
    private final TsFileSequenceReader reader;
    private final String filePath;
    private final long createdAt;

    public CachedReader(TsFileSequenceReader reader, String filePath) {
      this.reader = reader;
      this.filePath = filePath;
      this.createdAt = System.currentTimeMillis();
    }

    public TsFileSequenceReader getReader() {
      return reader;
    }

    public String getFilePath() {
      return filePath;
    }

    public long getCreatedAt() {
      return createdAt;
    }
  }

  /**
   * Constructs a TsFileReaderCache with the specified configuration properties.
   *
   * @param tsFileProperties configuration properties for cache settings
   */
  public TsFileReaderCache(TsFileProperties tsFileProperties) {
    this.tsFileProperties = tsFileProperties;
  }

  /** Initializes the Caffeine cache with configured settings after bean construction. */
  @PostConstruct
  public void init() {
    int maxSize = tsFileProperties.getCache().getReader().getMaxSize();
    int ttlMinutes = tsFileProperties.getCache().getReader().getTtlMinutes();

    logger.info(
        "Initializing TsFileReaderCache with maxSize={}, ttlMinutes={}", maxSize, ttlMinutes);

    this.readerCache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterAccess(ttlMinutes, TimeUnit.MINUTES)
            .removalListener(this::onRemoval)
            .recordStats()
            .build();
  }

  /** Closes all cached readers and clears the cache before bean destruction. */
  @PreDestroy
  public void destroy() {
    logger.info("Shutting down TsFileReaderCache, closing all cached readers");
    if (readerCache != null) {
      readerCache.invalidateAll();
      readerCache.cleanUp();
    }
  }

  /**
   * Gets a cached TsFileSequenceReader or creates a new one for the given fileId.
   *
   * <p>This method is thread-safe and handles concurrent access. If the reader for the given fileId
   * is not in the cache, a new reader is created and cached.
   *
   * @param fileId unique identifier for the file
   * @param filePath path to the TsFile
   * @return TsFileSequenceReader for the specified file
   * @throws IOException if the file cannot be opened or read
   */
  public TsFileSequenceReader getReader(String fileId, Path filePath) throws IOException {
    return getReader(fileId, filePath.toString());
  }

  /**
   * Gets a cached TsFileSequenceReader or creates a new one for the given fileId.
   *
   * <p>This method is thread-safe and handles concurrent access. If the reader for the given fileId
   * is not in the cache, a new reader is created and cached.
   *
   * @param fileId unique identifier for the file
   * @param filePath path to the TsFile as a string
   * @return TsFileSequenceReader for the specified file
   * @throws IOException if the file cannot be opened or read
   */
  public TsFileSequenceReader getReader(String fileId, String filePath) throws IOException {
    logger.debug("Getting reader for fileId={}, filePath={}", fileId, filePath);

    try {
      CachedReader cachedReader =
          readerCache.get(
              fileId,
              key -> {
                try {
                  logger.debug("Creating new TsFileSequenceReader for fileId={}", key);
                  TsFileSequenceReader reader = new TsFileSequenceReader(filePath);
                  return new CachedReader(reader, filePath);
                } catch (IOException e) {
                  logger.error("Failed to create TsFileSequenceReader for fileId={}", key, e);
                  throw new RuntimeException("Failed to open TsFile: " + filePath, e);
                }
              });

      if (cachedReader != null) {
        logger.debug("Returning cached reader for fileId={}", fileId);
        return cachedReader.getReader();
      }

      throw new IOException("Failed to get or create reader for fileId: " + fileId);
    } catch (RuntimeException e) {
      // Unwrap IOException from the lambda
      if (e.getCause() instanceof IOException ioException) {
        throw ioException;
      }
      throw new IOException("Failed to get reader for fileId: " + fileId, e);
    }
  }

  /**
   * Closes and removes a reader from the cache.
   *
   * <p>This method should be called when a file is deleted or when the reader is no longer needed.
   *
   * @param fileId unique identifier for the file to close
   */
  public void closeReader(String fileId) {
    logger.debug("Closing reader for fileId={}", fileId);
    readerCache.invalidate(fileId);
  }

  /**
   * Evicts stale readers from the cache based on LRU policy.
   *
   * <p>This method triggers cache cleanup, removing entries that have exceeded their TTL or when
   * the cache size exceeds the maximum.
   */
  public void evictStaleReaders() {
    logger.debug("Evicting stale readers from cache");
    readerCache.cleanUp();
  }

  /**
   * Checks if a reader for the given fileId is currently cached.
   *
   * @param fileId unique identifier for the file
   * @return true if the reader is cached, false otherwise
   */
  public boolean isCached(String fileId) {
    return readerCache.getIfPresent(fileId) != null;
  }

  /**
   * Returns the current number of cached readers.
   *
   * @return number of cached readers
   */
  public long getCacheSize() {
    return readerCache.estimatedSize();
  }

  /**
   * Returns cache statistics for monitoring purposes.
   *
   * @return cache statistics including hit rate, eviction count, etc.
   */
  public CacheStats getCacheStats() {
    var stats = readerCache.stats();
    return new CacheStats(
        stats.hitCount(),
        stats.missCount(),
        stats.loadSuccessCount(),
        stats.loadFailureCount(),
        stats.evictionCount(),
        stats.hitRate());
  }

  /**
   * Invalidates all cached readers.
   *
   * <p>This method closes all cached readers and clears the cache. Use with caution as it may
   * impact performance if called frequently.
   */
  public void invalidateAll() {
    logger.info("Invalidating all cached readers");
    readerCache.invalidateAll();
    readerCache.cleanUp();
  }

  /**
   * Callback method invoked when a cache entry is removed.
   *
   * <p>Ensures proper cleanup of TsFileSequenceReader resources when entries are evicted.
   *
   * @param fileId the file identifier of the removed entry
   * @param cachedReader the cached reader being removed
   * @param cause the reason for removal
   */
  private void onRemoval(String fileId, CachedReader cachedReader, RemovalCause cause) {
    if (cachedReader != null && cachedReader.getReader() != null) {
      logger.debug(
          "Removing reader from cache: fileId={}, cause={}, filePath={}",
          fileId,
          cause,
          cachedReader.getFilePath());
      try {
        cachedReader.getReader().close();
        logger.debug("Successfully closed reader for fileId={}", fileId);
      } catch (IOException e) {
        logger.warn(
            "Failed to close TsFileSequenceReader for fileId={}: {}", fileId, e.getMessage());
      }
    }
  }

  /** Cache statistics record for monitoring. */
  public record CacheStats(
      long hitCount,
      long missCount,
      long loadSuccessCount,
      long loadFailureCount,
      long evictionCount,
      double hitRate) {}
}
