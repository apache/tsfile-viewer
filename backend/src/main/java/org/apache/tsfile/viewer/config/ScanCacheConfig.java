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

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tsfile.viewer.service.ScanTask;
import org.apache.tsfile.viewer.tsfile.TsFileHealthChecker;

/**
 * Configuration for the scan result Caffeine cache.
 *
 * <p>Provides an in-memory cache for storing scan task results with configurable maximum size and
 * TTL. Cache entries are evicted after the configured write expiration time.
 *
 * <p>Validates: Requirements 7.2, 7.3
 */
@Configuration
public class ScanCacheConfig {

  private static final Logger logger = LoggerFactory.getLogger(ScanCacheConfig.class);

  /**
   * Creates a Caffeine cache bean for storing scan tasks.
   *
   * <p>The cache is configured with:
   *
   * <ul>
   *   <li>Maximum size from {@code tsfile.scan.cache-max-size} (default: 100)
   *   <li>Expire after write from {@code tsfile.scan.cache-ttl-minutes} (default: 60 minutes)
   *   <li>Statistics recording enabled for monitoring
   * </ul>
   *
   * @param tsFileProperties configuration properties containing scan cache settings
   * @return configured Caffeine cache for scan tasks
   */
  @Bean
  public Cache<String, ScanTask> scanTaskCache(TsFileProperties tsFileProperties) {
    int maxSize = tsFileProperties.getScan().getCacheMaxSize();
    int ttlMinutes = tsFileProperties.getScan().getCacheTtlMinutes();

    logger.info("Initializing scan task cache with maxSize={}, ttlMinutes={}", maxSize, ttlMinutes);

    return Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterAccess(ttlMinutes, TimeUnit.MINUTES)
        .recordStats()
        .build();
  }

  /**
   * Creates a TsFileHealthChecker bean.
   *
   * <p>The health checker is a pure Java class with no Spring dependencies, so it is registered as
   * a bean here to allow injection into Spring-managed services.
   *
   * @param tsFileProperties configuration supplying the chunk cap for the data-area traversal
   * @return a new TsFileHealthChecker instance
   */
  @Bean
  public TsFileHealthChecker tsFileHealthChecker(TsFileProperties tsFileProperties) {
    int maxChunks = tsFileProperties.getScan().getMaxChunks();
    logger.info("Initializing TsFileHealthChecker bean with maxChunks={}", maxChunks);
    return new TsFileHealthChecker(maxChunks);
  }
}
