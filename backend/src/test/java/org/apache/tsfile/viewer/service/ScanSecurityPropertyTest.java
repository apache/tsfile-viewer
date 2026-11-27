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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.tsfile.TsFileHealthChecker;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import net.jqwik.api.*;

/**
 * Property-based tests for whitelist path rejection security.
 *
 * <p>Verifies that ScanService rejects all paths that are not in the whitelist by throwing {@link
 * AccessDeniedException}. Both {@code startDirectoryScan} and {@code checkSingleFile} must enforce
 * this security constraint.
 *
 * <p><b>Validates: Requirements 7.1</b>
 */
class ScanSecurityPropertyTest {

  /**
   * Property 10: 白名单路径拒绝
   *
   * <p>For any path that is not in the whitelist directory list, {@code
   * ScanService.startDirectoryScan()} should reject the scan request and throw {@link
   * AccessDeniedException}.
   *
   * <p><b>Validates: Requirements 7.1</b>
   */
  @Property(tries = 100)
  @Label("Feature: tsfile-scan, Property 10: 白名单路径拒绝")
  void startDirectoryScanShouldRejectNonWhitelistedPaths(
      @ForAll("nonWhitelistedPaths") String path) {

    // Arrange: mock PathValidationService to always reject (path not in whitelist)
    PathValidationService pathValidationService = mock(PathValidationService.class);
    when(pathValidationService.isPathAllowed(anyString())).thenReturn(false);

    TsFileHealthChecker healthChecker = mock(TsFileHealthChecker.class);
    ScanTaskExecutor scanTaskExecutor = mock(ScanTaskExecutor.class);
    Cache<String, ScanTask> cache = Caffeine.newBuilder().maximumSize(10).build();

    ScanService scanService =
        new ScanService(cache, pathValidationService, healthChecker, scanTaskExecutor);

    // Act & Assert: should throw AccessDeniedException
    assertThatThrownBy(() -> scanService.startDirectoryScan(path))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Access denied");

    // Verify no task was created or executed
    verify(scanTaskExecutor, never()).executeScan(org.mockito.ArgumentMatchers.any());
  }

  /**
   * Property 10: 白名单路径拒绝 (checkSingleFile)
   *
   * <p>For any path that is not in the whitelist directory list, {@code
   * ScanService.checkSingleFile()} should reject the request and throw {@link
   * AccessDeniedException}.
   *
   * <p><b>Validates: Requirements 7.1</b>
   */
  @Property(tries = 100)
  @Label("Feature: tsfile-scan, Property 10: 白名单路径拒绝 - checkSingleFile")
  void checkSingleFileShouldRejectNonWhitelistedPaths(@ForAll("nonWhitelistedPaths") String path) {

    // Arrange: mock PathValidationService to always reject (path not in whitelist)
    PathValidationService pathValidationService = mock(PathValidationService.class);
    when(pathValidationService.isPathAllowed(anyString())).thenReturn(false);

    TsFileHealthChecker healthChecker = mock(TsFileHealthChecker.class);
    ScanTaskExecutor scanTaskExecutor = mock(ScanTaskExecutor.class);
    Cache<String, ScanTask> cache = Caffeine.newBuilder().maximumSize(10).build();

    ScanService scanService =
        new ScanService(cache, pathValidationService, healthChecker, scanTaskExecutor);

    // Act & Assert: should throw AccessDeniedException
    assertThatThrownBy(() -> scanService.checkSingleFile(path))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Access denied");

    // Verify health checker was never called
    verify(healthChecker, never()).check(org.mockito.ArgumentMatchers.any());
  }

  /**
   * Generates random paths that simulate non-whitelisted directories. Includes common sensitive
   * system paths, random absolute paths, and paths with various structures.
   */
  @Provide
  Arbitrary<String> nonWhitelistedPaths() {
    Arbitrary<String> systemPaths =
        Arbitraries.of(
            "/etc/passwd",
            "/etc/shadow",
            "/etc/hosts",
            "/root/.ssh/id_rsa",
            "/var/log/syslog",
            "/tmp/malicious",
            "/proc/self/environ",
            "/sys/kernel",
            "/boot/vmlinuz",
            "/usr/local/bin/secret");

    Arbitrary<String> randomAbsolutePaths =
        Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(20)
            .list()
            .ofMinSize(1)
            .ofMaxSize(5)
            .map(segments -> "/" + String.join("/", segments));

    Arbitrary<String> tsfilePaths =
        Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofMinLength(3)
            .ofMaxLength(15)
            .map(name -> "/unauthorized/" + name + ".tsfile");

    return Arbitraries.oneOf(systemPaths, randomAbsolutePaths, tsfilePaths);
  }
}
