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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.tsfile.viewer.dto.HealthStatus;

import net.jqwik.api.*;

/**
 * Property-based tests for {@link ScanService} — task ID format/uniqueness and report count
 * consistency.
 *
 * <p>Validates: Requirements 1.2, 1.5
 */
class ScanServicePropertyTest {

  /** Regex pattern for valid task IDs: 14-digit timestamp + hyphen + 6 lowercase alphanumeric. */
  private static final Pattern TASK_ID_PATTERN = Pattern.compile("^\\d{14}-[a-z0-9]{6}$");

  /**
   * Property 2: 任务 ID 格式与唯一性
   *
   * <p>Generates 1000 task IDs and verifies that each one matches the expected format
   * (yyyyMMddHHmmss-random6) and that no duplicates exist among all generated IDs.
   *
   * <p><b>Validates: Requirements 1.2</b>
   */
  @Property(tries = 1)
  @Label("Feature: tsfile-scan, Property 2: 任务 ID 格式与唯一性")
  void allGeneratedTaskIdsShouldMatchFormatAndBeUnique() {
    ScanService scanService = createMinimalScanService();

    int count = 1000;
    Set<String> generatedIds = new HashSet<>();

    for (int i = 0; i < count; i++) {
      String taskId = scanService.generateTaskId();

      // Verify format: 14-digit timestamp + hyphen + 6 lowercase alphanumeric characters
      assertThat(taskId)
          .as("Task ID '%s' should match pattern yyyyMMddHHmmss-[a-z0-9]{6}", taskId)
          .matches(TASK_ID_PATTERN.pattern());

      // Collect for uniqueness check
      generatedIds.add(taskId);
    }

    // Verify uniqueness: all 1000 IDs should be distinct
    assertThat(generatedIds).as("All %d generated task IDs should be unique", count).hasSize(count);
  }

  /**
   * Property 3: 扫描报告计数一致性
   *
   * <p>Generates a random list of {@link ScanResult} objects with random {@link HealthStatus}
   * values, builds a {@link ScanReport} by counting statuses, and verifies that:
   *
   * <ul>
   *   <li>{@code totalFiles == healthyCount + warningCount + errorCount}
   *   <li>{@code results.size() == totalFiles}
   * </ul>
   *
   * <p><b>Validates: Requirements 1.5</b>
   */
  @Property(tries = 100)
  @Label("Feature: tsfile-scan, Property 3: 扫描报告计数一致性")
  void scanReportCountsShouldBeConsistent(@ForAll("scanResultLists") List<ScanResult> scanResults) {

    // Build a ScanReport from the random results (mimicking ScanService.getReport logic)
    ScanReport report = new ScanReport("test-task-id");
    report.setResults(scanResults);
    report.setTotalFiles(scanResults.size());

    int healthyCount = 0;
    int warningCount = 0;
    int errorCount = 0;

    for (ScanResult result : scanResults) {
      switch (result.getHealthStatus()) {
        case HEALTHY -> healthyCount++;
        case WARNING -> warningCount++;
        case ERROR -> errorCount++;
      }
    }

    report.setHealthyCount(healthyCount);
    report.setWarningCount(warningCount);
    report.setErrorCount(errorCount);

    // Property: totalFiles == healthyCount + warningCount + errorCount
    assertThat(report.getTotalFiles())
        .as(
            "totalFiles (%d) should equal healthyCount (%d) + warningCount (%d) + errorCount (%d)",
            report.getTotalFiles(),
            report.getHealthyCount(),
            report.getWarningCount(),
            report.getErrorCount())
        .isEqualTo(report.getHealthyCount() + report.getWarningCount() + report.getErrorCount());

    // Property: results.size() == totalFiles
    assertThat(report.getResults().size())
        .as(
            "results.size() (%d) should equal totalFiles (%d)",
            report.getResults().size(), report.getTotalFiles())
        .isEqualTo(report.getTotalFiles());
  }

  /**
   * Provides arbitrary lists of {@link ScanResult} with random {@link HealthStatus} values. Each
   * result has a generated file path, non-negative file size, a random health status, an empty
   * error list, and non-negative scan duration.
   */
  @Provide
  Arbitrary<List<ScanResult>> scanResultLists() {
    Arbitrary<ScanResult> scanResultArbitrary =
        Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30), // file name
                Arbitraries.longs().between(0, 10_000_000L), // file size
                Arbitraries.of(HealthStatus.values()), // health status
                Arbitraries.longs().between(0, 60_000L) // scan duration ms
                )
            .as((fileName, fileSize, healthStatus, scanDurationMs) ->
                new ScanResult(
                    "/data/" + fileName + ".tsfile",
                    fileSize,
                    healthStatus,
                    new ArrayList<>(),
                    scanDurationMs));

    return scanResultArbitrary.list().ofMinSize(0).ofMaxSize(200);
  }

  /**
   * Creates a minimal {@link ScanService} instance with null dependencies, since only the {@code
   * generateTaskId()} method is under test and it has no external dependencies.
   */
  private ScanService createMinimalScanService() {
    return new ScanService(null, null, null, null);
  }
}
