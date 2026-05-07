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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tsfile.viewer.dto.ErrorSeverity;
import org.apache.tsfile.viewer.dto.HealthStatus;
import org.apache.tsfile.viewer.dto.ScanErrorType;
import org.apache.tsfile.viewer.service.ScanResult;

/**
 * Unit tests for {@link TsFileHealthChecker}.
 *
 * <p>Validates: Requirements 2.2 (health detection), 9.2 (boundary cases — severely corrupted
 * files).
 */
class TsFileHealthCheckerTest {

  private TsFileHealthChecker checker;

  /**
   * Resolves the project root directory by walking up from the current working directory until we
   * find the {@code tsfile-samples} folder.
   */
  private static Path resolveProjectRoot() {
    Path cwd = Path.of("").toAbsolutePath();
    // When running from backend/, go up one level to project root
    if (cwd.endsWith("backend")) {
      return cwd.getParent();
    }
    return cwd;
  }

  private static final Path PROJECT_ROOT = resolveProjectRoot();

  @BeforeEach
  void setUp() {
    checker = new TsFileHealthChecker();
  }

  // -------------------------------------------------------------------------
  // Valid TsFile tests — expect HEALTHY
  // -------------------------------------------------------------------------

  @Nested
  class ValidTsFileTests {

    @Test
    @EnabledIf("org.apache.tsfile.viewer.tsfile.TsFileHealthCheckerTest#treeModelSampleExists")
    void check_validTreeModelFile_returnsHealthy() {
      Path validFile =
          PROJECT_ROOT.resolve("tsfile-samples/tsfiles/tree/root.stock/1768435200963-1-0-0.tsfile");
      assertThat(validFile).exists();

      ScanResult result = checker.check(validFile);

      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
      assertThat(result.getErrors()).isEmpty();
      assertThat(result.getFilePath()).isEqualTo(validFile.toString());
      assertThat(result.getFileSize()).isGreaterThan(0);
      assertThat(result.getScanDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @EnabledIf("org.apache.tsfile.viewer.tsfile.TsFileHealthCheckerTest#autoModelSampleExists")
    void quickCheck_validAutoModelFile_returnsHealthy() {
      Path validFile =
          PROJECT_ROOT.resolve("tsfile-samples/tsfiles/auto/1766332793694-1-11-51.tsfile");
      assertThat(validFile).exists();

      ScanResult result = checker.quickCheck(validFile);

      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
      assertThat(result.getErrors()).isEmpty();
      assertThat(result.getFilePath()).isEqualTo(validFile.toString());
      assertThat(result.getFileSize()).isGreaterThan(0);
      assertThat(result.getScanDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @EnabledIf("org.apache.tsfile.viewer.tsfile.TsFileHealthCheckerTest#tableModelSampleExists")
    void quickCheck_validTableModelFile_returnsHealthy() {
      Path validFile =
          PROJECT_ROOT.resolve("tsfile-samples/tsfiles/table/wind/1765943964260-1-0-0.tsfile");
      assertThat(validFile).exists();

      ScanResult result = checker.quickCheck(validFile);

      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
      assertThat(result.getErrors()).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // Boundary / error cases
  // -------------------------------------------------------------------------

  @Nested
  class BoundaryCaseTests {

    @TempDir Path tempDir;

    @Test
    void check_nonExistentFile_returnsErrorWithFormatIncompatible() {
      Path nonExistent = tempDir.resolve("does-not-exist.tsfile");

      ScanResult result = checker.check(nonExistent);

      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.ERROR);
      assertThat(result.getErrors()).isNotEmpty();
      assertThat(result.getErrors().get(0).getErrorType())
          .isEqualTo(ScanErrorType.FORMAT_INCOMPATIBLE);
      assertThat(result.getErrors().get(0).getSeverity()).isEqualTo(ErrorSeverity.CRITICAL);
      assertThat(result.getFileSize()).isEqualTo(0);
    }

    @Test
    void check_emptyFile_returnsErrorWithFormatIncompatible() throws IOException {
      Path emptyFile = tempDir.resolve("empty.tsfile");
      Files.createFile(emptyFile);

      ScanResult result = checker.check(emptyFile);

      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.ERROR);
      assertThat(result.getErrors()).isNotEmpty();
      assertThat(result.getErrors().get(0).getErrorType())
          .isEqualTo(ScanErrorType.FORMAT_INCOMPATIBLE);
      assertThat(result.getErrors().get(0).getSeverity()).isEqualTo(ErrorSeverity.CRITICAL);
      assertThat(result.getFileSize()).isEqualTo(0);
    }

    @Test
    void check_nonTsFile_returnsErrorWithFormatIncompatible() throws IOException {
      Path textFile = tempDir.resolve("not-a-tsfile.tsfile");
      Files.writeString(textFile, "This is not a TsFile, just plain text content.");

      ScanResult result = checker.check(textFile);

      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.ERROR);
      assertThat(result.getErrors()).isNotEmpty();
      assertThat(result.getErrors().get(0).getErrorType())
          .isEqualTo(ScanErrorType.FORMAT_INCOMPATIBLE);
      assertThat(result.getErrors().get(0).getSeverity()).isEqualTo(ErrorSeverity.CRITICAL);
    }

    @Test
    void quickCheck_nonExistentFile_returnsErrorWithFormatIncompatible() {
      Path nonExistent = tempDir.resolve("does-not-exist.tsfile");

      ScanResult result = checker.quickCheck(nonExistent);

      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.ERROR);
      assertThat(result.getErrors()).isNotEmpty();
      assertThat(result.getErrors().get(0).getErrorType())
          .isEqualTo(ScanErrorType.FORMAT_INCOMPATIBLE);
      assertThat(result.getErrors().get(0).getSeverity()).isEqualTo(ErrorSeverity.CRITICAL);
    }

    @Test
    void quickCheck_emptyFile_returnsErrorWithFormatIncompatible() throws IOException {
      Path emptyFile = tempDir.resolve("empty.tsfile");
      Files.createFile(emptyFile);

      ScanResult result = checker.quickCheck(emptyFile);

      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.ERROR);
      assertThat(result.getErrors()).isNotEmpty();
      assertThat(result.getErrors().get(0).getErrorType())
          .isEqualTo(ScanErrorType.FORMAT_INCOMPATIBLE);
      assertThat(result.getErrors().get(0).getSeverity()).isEqualTo(ErrorSeverity.CRITICAL);
    }
  }

  // -------------------------------------------------------------------------
  // ScanResult structure validation
  // -------------------------------------------------------------------------

  @Nested
  class ScanResultStructureTests {

    @TempDir Path tempDir;

    @Test
    @EnabledIf("org.apache.tsfile.viewer.tsfile.TsFileHealthCheckerTest#treeModelSampleExists")
    void check_alwaysPopulatesAllFields() {
      Path validFile =
          PROJECT_ROOT.resolve("tsfile-samples/tsfiles/tree/root.stock/1768435200963-1-0-0.tsfile");
      assertThat(validFile).exists();

      ScanResult result = checker.check(validFile);

      assertThat(result.getFilePath()).isNotNull().isNotEmpty();
      assertThat(result.getHealthStatus()).isNotNull();
      assertThat(result.getErrors()).isNotNull();
      assertThat(result.getFileSize()).isGreaterThanOrEqualTo(0);
      assertThat(result.getScanDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void check_errorResult_alwaysPopulatesAllFields() throws IOException {
      Path textFile = tempDir.resolve("garbage.tsfile");
      Files.writeString(textFile, "garbage content");

      ScanResult result = checker.check(textFile);

      assertThat(result.getFilePath()).isNotNull().isNotEmpty();
      assertThat(result.getHealthStatus()).isNotNull();
      assertThat(result.getErrors()).isNotNull().isNotEmpty();
      assertThat(result.getFileSize()).isGreaterThanOrEqualTo(0);
      assertThat(result.getScanDurationMs()).isGreaterThanOrEqualTo(0);

      // Verify each error has all fields populated
      result
          .getErrors()
          .forEach(
              error -> {
                assertThat(error.getErrorType()).isNotNull();
                assertThat(error.getLocation()).isNotNull().isNotEmpty();
                assertThat(error.getDescription()).isNotNull().isNotEmpty();
                assertThat(error.getSeverity()).isNotNull();
              });
    }
  }

  // -------------------------------------------------------------------------
  // Condition methods for @EnabledIf
  // -------------------------------------------------------------------------

  static boolean treeModelSampleExists() {
    Path sampleFile =
        PROJECT_ROOT.resolve("tsfile-samples/tsfiles/tree/root.stock/1768435200963-1-0-0.tsfile");
    return Files.exists(sampleFile) && Files.isRegularFile(sampleFile);
  }

  static boolean autoModelSampleExists() {
    Path sampleFile =
        PROJECT_ROOT.resolve("tsfile-samples/tsfiles/auto/1766332793694-1-11-51.tsfile");
    return Files.exists(sampleFile) && Files.isRegularFile(sampleFile);
  }

  static boolean tableModelSampleExists() {
    Path sampleFile =
        PROJECT_ROOT.resolve("tsfile-samples/tsfiles/table/wind/1765943964260-1-0-0.tsfile");
    return Files.exists(sampleFile) && Files.isRegularFile(sampleFile);
  }
}
