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

package org.apache.tsfile.viewer.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for TsFile Viewer.
 *
 * <p>Binds to the {@code tsfile} prefix in application.yml and provides configuration for:
 *
 * <ul>
 *   <li>Allowed directories whitelist for security
 *   <li>Upload directory for file storage
 *   <li>Cache settings for metadata and readers
 *   <li>Query timeout and result size limits
 * </ul>
 *
 * <p>Validates: Requirement 7.6 (Directory whitelist security)
 */
@ConfigurationProperties(prefix = "tsfile")
public class TsFileProperties {

  /** List of allowed directories for file access. Paths outside this whitelist are rejected. */
  private List<String> allowedDirectories = new ArrayList<>();

  /** Directory where uploaded TsFile files are stored. */
  private String uploadDirectory;

  /** Cache configuration settings. */
  private CacheProperties cache = new CacheProperties();

  /** Query configuration settings. */
  private QueryProperties query = new QueryProperties();

  /** Scan configuration settings for file health checking. */
  private ScanProperties scan = new ScanProperties();

  public List<String> getAllowedDirectories() {
    return allowedDirectories;
  }

  public void setAllowedDirectories(List<String> allowedDirectories) {
    this.allowedDirectories = allowedDirectories;
  }

  public String getUploadDirectory() {
    return uploadDirectory;
  }

  public void setUploadDirectory(String uploadDirectory) {
    this.uploadDirectory = uploadDirectory;
  }

  public CacheProperties getCache() {
    return cache;
  }

  public void setCache(CacheProperties cache) {
    this.cache = cache;
  }

  public QueryProperties getQuery() {
    return query;
  }

  public void setQuery(QueryProperties query) {
    this.query = query;
  }

  public ScanProperties getScan() {
    return scan;
  }

  public void setScan(ScanProperties scan) {
    this.scan = scan;
  }

  /** Cache configuration properties. */
  public static class CacheProperties {

    /** Metadata cache settings. */
    private MetadataCacheProperties metadata = new MetadataCacheProperties();

    /** Reader cache settings. */
    private ReaderCacheProperties reader = new ReaderCacheProperties();

    public MetadataCacheProperties getMetadata() {
      return metadata;
    }

    public void setMetadata(MetadataCacheProperties metadata) {
      this.metadata = metadata;
    }

    public ReaderCacheProperties getReader() {
      return reader;
    }

    public void setReader(ReaderCacheProperties reader) {
      this.reader = reader;
    }
  }

  /** Metadata cache configuration. */
  public static class MetadataCacheProperties {

    /** Maximum number of entries in the metadata cache. */
    private int maxSize = 1000;

    /** Time-to-live in minutes for metadata cache entries. */
    private int ttlMinutes = 60;

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
    }

    public int getTtlMinutes() {
      return ttlMinutes;
    }

    public void setTtlMinutes(int ttlMinutes) {
      this.ttlMinutes = ttlMinutes;
    }
  }

  /** Reader cache configuration. */
  public static class ReaderCacheProperties {

    /** Maximum number of entries in the reader cache. */
    private int maxSize = 100;

    /** Time-to-live in minutes for reader cache entries. */
    private int ttlMinutes = 30;

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
    }

    public int getTtlMinutes() {
      return ttlMinutes;
    }

    public void setTtlMinutes(int ttlMinutes) {
      this.ttlMinutes = ttlMinutes;
    }
  }

  /** Query configuration properties. */
  public static class QueryProperties {

    /** Query timeout in seconds. */
    private int timeoutSeconds = 30;

    /** Maximum number of results returned by a query. */
    private int maxResultSize = 10000;

    /** Default page size for paginated queries. */
    private int defaultPageSize = 100;

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxResultSize() {
      return maxResultSize;
    }

    public void setMaxResultSize(int maxResultSize) {
      this.maxResultSize = maxResultSize;
    }

    public int getDefaultPageSize() {
      return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
      this.defaultPageSize = defaultPageSize;
    }
  }

  /**
   * Scan configuration properties for file health checking.
   *
   * <p>Controls concurrency, timeouts, and cache settings for the scan feature.
   *
   * <p>Validates: Requirements 3.1, 7.2, 7.3
   */
  public static class ScanProperties {

    /** Maximum number of concurrent scan tasks. */
    private int maxConcurrent = 2;

    /** Timeout in seconds for scanning a single file. */
    private int singleFileTimeoutSeconds = 30;

    /** Timeout in minutes for an entire scan task. */
    private int taskTimeoutMinutes = 30;

    /** Maximum number of scan results to cache. */
    private int cacheMaxSize = 100;

    /** Time-to-live in minutes for cached scan results. */
    private int cacheTtlMinutes = 60;

    public int getMaxConcurrent() {
      return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
      this.maxConcurrent = maxConcurrent;
    }

    public int getSingleFileTimeoutSeconds() {
      return singleFileTimeoutSeconds;
    }

    public void setSingleFileTimeoutSeconds(int singleFileTimeoutSeconds) {
      this.singleFileTimeoutSeconds = singleFileTimeoutSeconds;
    }

    public int getTaskTimeoutMinutes() {
      return taskTimeoutMinutes;
    }

    public void setTaskTimeoutMinutes(int taskTimeoutMinutes) {
      this.taskTimeoutMinutes = taskTimeoutMinutes;
    }

    public int getCacheMaxSize() {
      return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
      this.cacheMaxSize = cacheMaxSize;
    }

    public int getCacheTtlMinutes() {
      return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
      this.cacheTtlMinutes = cacheTtlMinutes;
    }
  }
}
