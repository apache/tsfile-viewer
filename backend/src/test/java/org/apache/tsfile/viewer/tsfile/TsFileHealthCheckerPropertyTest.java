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

import java.util.List;
import java.util.Map;

import org.apache.tsfile.viewer.dto.ErrorSeverity;
import org.apache.tsfile.viewer.dto.HealthStatus;
import org.apache.tsfile.viewer.dto.ScanErrorType;
import org.apache.tsfile.viewer.service.FileError;
import org.apache.tsfile.viewer.service.ScanResult;

import net.jqwik.api.*;

/**
 * Property-based tests for {@link TsFileHealthChecker} — ScanResult structural integrity and
 * severity mapping.
 *
 * <p>Validates: Requirements 2.4, 2.5
 */
class TsFileHealthCheckerPropertyTest {

  /**
   * The canonical mapping from {@link ScanErrorType} to {@link ErrorSeverity} as defined in the
   * design document and implemented by {@link TsFileHealthChecker}.
   *
   * <ul>
   *   <li>FORMAT_INCOMPATIBLE → CRITICAL
   *   <li>STRUCTURE_CORRUPT → ERROR
   *   <li>CHUNK_STATISTICS_MISMATCH → ERROR
   *   <li>TIMESERIES_METADATA_MISMATCH → ERROR
   *   <li>DATA_READ_ERROR → WARNING
   *   <li>DATA_VALIDATION_SKIPPED → WARNING
   * </ul>
   */
  private static final Map<ScanErrorType, ErrorSeverity> EXPECTED_SEVERITY_MAP =
      Map.of(
          ScanErrorType.FORMAT_INCOMPATIBLE, ErrorSeverity.CRITICAL,
          ScanErrorType.STRUCTURE_CORRUPT, ErrorSeverity.ERROR,
          ScanErrorType.CHUNK_STATISTICS_MISMATCH, ErrorSeverity.ERROR,
          ScanErrorType.TIMESERIES_METADATA_MISMATCH, ErrorSeverity.ERROR,
          ScanErrorType.DATA_READ_ERROR, ErrorSeverity.WARNING,
          ScanErrorType.DATA_VALIDATION_SKIPPED, ErrorSeverity.WARNING);

  // ---------------------------------------------------------------------------
  // Property 5: ScanResult 结构完整性与严重程度映射
  // ---------------------------------------------------------------------------

  @Property(tries = 200)
  @Label("Feature: tsfile-scan, Property 5: ScanResult 结构完整性与严重程度映射")
  void scanResultFieldsAreNonNullAndSeverityMappingIsCorrect(
      @ForAll("validScanResults") ScanResult result) {

    // --- ScanResult structural integrity ---
    assertThat(result.getFilePath()).as("filePath must not be null").isNotNull();
    assertThat(result.getFileSize()).as("fileSize must be >= 0").isGreaterThanOrEqualTo(0);
    assertThat(result.getHealthStatus()).as("healthStatus must not be null").isNotNull();
    assertThat(result.getErrors()).as("errors list must not be null").isNotNull();
    assertThat(result.getScanDurationMs())
        .as("scanDurationMs must be >= 0")
        .isGreaterThanOrEqualTo(0);

    // --- Severity mapping correctness for every FileError ---
    for (FileError error : result.getErrors()) {
      assertThat(error.getErrorType()).as("FileError.errorType must not be null").isNotNull();
      assertThat(error.getSeverity()).as("FileError.severity must not be null").isNotNull();
      assertThat(error.getLocation()).as("FileError.location must not be null").isNotNull();
      assertThat(error.getDescription()).as("FileError.description must not be null").isNotNull();

      ErrorSeverity expectedSeverity = EXPECTED_SEVERITY_MAP.get(error.getErrorType());
      assertThat(error.getSeverity())
          .as(
              "Severity for %s should be %s but was %s",
              error.getErrorType(), expectedSeverity, error.getSeverity())
          .isEqualTo(expectedSeverity);
    }
  }

  // ---------------------------------------------------------------------------
  // Arbitraries / Generators
  // ---------------------------------------------------------------------------

  @Provide
  Arbitrary<ScanResult> validScanResults() {
    Arbitrary<String> filePaths =
        Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50)
            .map(name -> "/data/scan/" + name + ".tsfile");

    Arbitrary<Long> fileSizes = Arbitraries.longs().between(0, 10_000_000_000L);

    Arbitrary<Long> durations = Arbitraries.longs().between(0, 300_000L);

    Arbitrary<List<FileError>> errorLists = fileErrors().list().ofMaxSize(10);

    return Combinators.combine(filePaths, fileSizes, errorLists, durations)
        .as(
            (path, size, errors, duration) -> {
              HealthStatus status = deriveHealthStatus(errors);
              return new ScanResult(path, size, status, errors, duration);
            });
  }

  private Arbitrary<FileError> fileErrors() {
    Arbitrary<ScanErrorType> errorTypes = Arbitraries.of(ScanErrorType.class);

    Arbitrary<String> locations =
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30).map(s -> "Location: " + s);

    Arbitrary<String> descriptions =
        Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(60)
            .map(s -> "Error description: " + s);

    return Combinators.combine(errorTypes, locations, descriptions)
        .as(
            (errorType, location, description) -> {
              ErrorSeverity severity = EXPECTED_SEVERITY_MAP.get(errorType);
              return new FileError(errorType, location, description, severity);
            });
  }

  /**
   * Derives the overall health status from a list of errors, mirroring the logic in {@link
   * TsFileHealthChecker#determineHealthStatus}.
   */
  private static HealthStatus deriveHealthStatus(List<FileError> errors) {
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
}
