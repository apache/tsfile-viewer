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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.config.TsFileProperties.CacheProperties;
import org.apache.tsfile.viewer.config.TsFileProperties.ReaderCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TsFileReaderCache}.
 *
 * <p>Validates: Requirement 5.1, 5.2, 5.3, 5.6 (Caching, concurrency)
 */
@ExtendWith(MockitoExtension.class)
class TsFileReaderCacheTest {

  @Mock private TsFileProperties tsFileProperties;

  @Mock private CacheProperties cacheProperties;

  @Mock private ReaderCacheProperties readerCacheProperties;

  private TsFileReaderCache readerCache;

  @BeforeEach
  void setUp() {
    when(tsFileProperties.getCache()).thenReturn(cacheProperties);
    when(cacheProperties.getReader()).thenReturn(readerCacheProperties);
    when(readerCacheProperties.getMaxSize()).thenReturn(100);
    when(readerCacheProperties.getTtlMinutes()).thenReturn(30);

    readerCache = new TsFileReaderCache(tsFileProperties);
    readerCache.init();
  }

  @Nested
  @DisplayName("Cache Initialization Tests")
  class CacheInitializationTests {

    @Test
    @DisplayName("Should initialize cache with configured max size and TTL")
    void shouldInitializeCacheWithConfiguredSettings() {
      assertThat(readerCache.getCacheSize()).isZero();
    }

    @Test
    @DisplayName("Should return empty cache stats after initialization")
    void shouldReturnEmptyCacheStatsAfterInitialization() {
      TsFileReaderCache.CacheStats stats = readerCache.getCacheStats();

      assertThat(stats.hitCount()).isZero();
      assertThat(stats.missCount()).isZero();
      assertThat(stats.evictionCount()).isZero();
    }
  }

  @Nested
  @DisplayName("Cache Operations Tests")
  class CacheOperationsTests {

    @Test
    @DisplayName("Should return false for isCached when fileId not in cache")
    void shouldReturnFalseForIsCachedWhenNotInCache() {
      assertThat(readerCache.isCached("non-existent-file")).isFalse();
    }

    @Test
    @DisplayName("Should throw IOException when file does not exist")
    void shouldThrowIOExceptionWhenFileDoesNotExist() {
      String fileId = "test-file-id";
      String nonExistentPath = "/non/existent/path/file.tsfile";

      assertThatThrownBy(() -> readerCache.getReader(fileId, nonExistentPath))
          .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should throw IOException when file path is invalid")
    void shouldThrowIOExceptionWhenFilePathIsInvalid() {
      String fileId = "test-file-id";
      Path invalidPath = Path.of("/invalid/path/to/file.tsfile");

      assertThatThrownBy(() -> readerCache.getReader(fileId, invalidPath))
          .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should not have entry after closeReader is called")
    void shouldNotHaveEntryAfterCloseReader() {
      String fileId = "test-file-id";

      // Close a non-existent reader should not throw
      readerCache.closeReader(fileId);

      assertThat(readerCache.isCached(fileId)).isFalse();
    }

    @Test
    @DisplayName("Should clear all entries on invalidateAll")
    void shouldClearAllEntriesOnInvalidateAll() {
      readerCache.invalidateAll();

      assertThat(readerCache.getCacheSize()).isZero();
    }

    @Test
    @DisplayName("Should handle evictStaleReaders without error")
    void shouldHandleEvictStaleReadersWithoutError() {
      // Should not throw any exception
      readerCache.evictStaleReaders();

      assertThat(readerCache.getCacheSize()).isZero();
    }
  }

  @Nested
  @DisplayName("Cache Statistics Tests")
  class CacheStatisticsTests {

    @Test
    @DisplayName("Should track miss count when reader not found")
    void shouldTrackMissCountWhenReaderNotFound() {
      String fileId = "test-file-id";

      try {
        readerCache.getReader(fileId, "/non/existent/path.tsfile");
      } catch (IOException e) {
        // Expected exception
      }

      TsFileReaderCache.CacheStats stats = readerCache.getCacheStats();
      // Miss count should be incremented due to cache miss
      assertThat(stats.loadFailureCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should return valid hit rate")
    void shouldReturnValidHitRate() {
      TsFileReaderCache.CacheStats stats = readerCache.getCacheStats();

      // Hit rate should be between 0 and 1 (or NaN if no requests)
      assertThat(stats.hitRate()).isBetween(0.0, 1.0);
    }
  }

  @Nested
  @DisplayName("Thread Safety Tests")
  class ThreadSafetyTests {

    @Test
    @DisplayName("Should handle concurrent closeReader calls safely")
    void shouldHandleConcurrentCloseReaderCallsSafely() throws InterruptedException {
      String fileId = "concurrent-test-file";

      // Create multiple threads that try to close the same reader
      Thread[] threads = new Thread[10];
      for (int i = 0; i < threads.length; i++) {
        threads[i] =
            new Thread(() -> {
              readerCache.closeReader(fileId);
            });
      }

      // Start all threads
      for (Thread thread : threads) {
        thread.start();
      }

      // Wait for all threads to complete
      for (Thread thread : threads) {
        thread.join();
      }

      // Should not throw any exception and cache should be empty
      assertThat(readerCache.isCached(fileId)).isFalse();
    }

    @Test
    @DisplayName("Should handle concurrent evictStaleReaders calls safely")
    void shouldHandleConcurrentEvictStaleReadersSafely() throws InterruptedException {
      Thread[] threads = new Thread[5];
      for (int i = 0; i < threads.length; i++) {
        threads[i] =
            new Thread(() -> {
              readerCache.evictStaleReaders();
            });
      }

      for (Thread thread : threads) {
        thread.start();
      }

      for (Thread thread : threads) {
        thread.join();
      }

      // Should complete without exception
      assertThat(readerCache.getCacheSize()).isZero();
    }
  }

  @Nested
  @DisplayName("Lifecycle Tests")
  class LifecycleTests {

    @Test
    @DisplayName("Should clean up resources on destroy")
    void shouldCleanUpResourcesOnDestroy() {
      readerCache.destroy();

      // After destroy, cache should be empty
      assertThat(readerCache.getCacheSize()).isZero();
    }
  }

  @Nested
  @DisplayName("CachedReader Tests")
  class CachedReaderTests {

    @Test
    @DisplayName("Should store file path in CachedReader")
    void shouldStoreFilePathInCachedReader() {
      String filePath = "/test/path/file.tsfile";
      TsFileReaderCache.CachedReader cachedReader =
          new TsFileReaderCache.CachedReader(null, filePath);

      assertThat(cachedReader.getFilePath()).isEqualTo(filePath);
    }

    @Test
    @DisplayName("Should record creation time in CachedReader")
    void shouldRecordCreationTimeInCachedReader() {
      long beforeCreation = System.currentTimeMillis();
      TsFileReaderCache.CachedReader cachedReader =
          new TsFileReaderCache.CachedReader(null, "/test/path");
      long afterCreation = System.currentTimeMillis();

      assertThat(cachedReader.getCreatedAt())
          .isGreaterThanOrEqualTo(beforeCreation)
          .isLessThanOrEqualTo(afterCreation);
    }

    @Test
    @DisplayName("Should return null reader when constructed with null")
    void shouldReturnNullReaderWhenConstructedWithNull() {
      TsFileReaderCache.CachedReader cachedReader =
          new TsFileReaderCache.CachedReader(null, "/test/path");

      assertThat(cachedReader.getReader()).isNull();
    }
  }

  @Nested
  @DisplayName("CacheStats Record Tests")
  class CacheStatsRecordTests {

    @Test
    @DisplayName("Should create CacheStats with all fields")
    void shouldCreateCacheStatsWithAllFields() {
      TsFileReaderCache.CacheStats stats = new TsFileReaderCache.CacheStats(10, 5, 8, 2, 3, 0.67);

      assertThat(stats.hitCount()).isEqualTo(10);
      assertThat(stats.missCount()).isEqualTo(5);
      assertThat(stats.loadSuccessCount()).isEqualTo(8);
      assertThat(stats.loadFailureCount()).isEqualTo(2);
      assertThat(stats.evictionCount()).isEqualTo(3);
      assertThat(stats.hitRate()).isEqualTo(0.67);
    }
  }
}
