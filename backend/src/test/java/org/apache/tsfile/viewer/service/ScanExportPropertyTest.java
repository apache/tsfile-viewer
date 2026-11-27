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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tsfile.viewer.dto.ErrorSeverity;
import org.apache.tsfile.viewer.dto.HealthStatus;
import org.apache.tsfile.viewer.dto.ScanErrorType;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.jqwik.api.*;

/**
 * Property-based tests for scan report export — JSON serialization round-trip consistency and CSV
 * format completeness.
 *
 * <p>Validates: Requirements 4.1, 4.2, 4.3
 */
class ScanExportPropertyTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Property 7: JSON 导出序列化往返一致性
   *
   * <p>For any valid ScanReport, serializing it to JSON and then deserializing back should produce
   * a semantically equivalent object — all fields must match.
   *
   * <p><b>Validates: Requirements 4.1, 4.3</b>
   */
  @Property(tries = 100)
  @Label("Feature: tsfile-scan, Property 7: JSON 导出序列化往返一致性")
  void jsonRoundTripShouldPreserveAllFields(@ForAll("scanReports") ScanReport original)
      throws Exception {

    // Serialize to JSON
    byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(original);

    // Deserialize back
    ScanReport deserialized = objectMapper.readValue(jsonBytes, ScanReport.class);

    // Verify top-level fields
    assertThat(deserialized.getTaskId())
        .as("taskId should survive round-trip")
        .isEqualTo(original.getTaskId());

    assertThat(deserialized.getTotalFiles())
        .as("totalFiles should survive round-trip")
        .isEqualTo(original.getTotalFiles());

    assertThat(deserialized.getHealthyCount())
        .as("healthyCount should survive round-trip")
        .isEqualTo(original.getHealthyCount());

    assertThat(deserialized.getWarningCount())
        .as("warningCount should survive round-trip")
        .isEqualTo(original.getWarningCount());

    assertThat(deserialized.getErrorCount())
        .as("errorCount should survive round-trip")
        .isEqualTo(original.getErrorCount());

    assertThat(deserialized.getTotalDurationMs())
        .as("totalDurationMs should survive round-trip")
        .isEqualTo(original.getTotalDurationMs());

    // Verify errorTypeDistribution map
    assertThat(deserialized.getErrorTypeDistribution())
        .as("errorTypeDistribution should survive round-trip")
        .isEqualTo(original.getErrorTypeDistribution());

    // Verify results list size
    assertThat(deserialized.getResults())
        .as("results list size should survive round-trip")
        .hasSameSizeAs(original.getResults());

    // Verify each ScanResult
    for (int i = 0; i < original.getResults().size(); i++) {
      ScanResult origResult = original.getResults().get(i);
      ScanResult deserResult = deserialized.getResults().get(i);

      assertThat(deserResult.getFilePath())
          .as("results[%d].filePath should survive round-trip", i)
          .isEqualTo(origResult.getFilePath());

      assertThat(deserResult.getFileSize())
          .as("results[%d].fileSize should survive round-trip", i)
          .isEqualTo(origResult.getFileSize());

      assertThat(deserResult.getHealthStatus())
          .as("results[%d].healthStatus should survive round-trip", i)
          .isEqualTo(origResult.getHealthStatus());

      assertThat(deserResult.getScanDurationMs())
          .as("results[%d].scanDurationMs should survive round-trip", i)
          .isEqualTo(origResult.getScanDurationMs());

      // Verify each FileError in the result
      assertThat(deserResult.getErrors())
          .as("results[%d].errors list size should survive round-trip", i)
          .hasSameSizeAs(origResult.getErrors());

      for (int j = 0; j < origResult.getErrors().size(); j++) {
        FileError origError = origResult.getErrors().get(j);
        FileError deserError = deserResult.getErrors().get(j);

        assertThat(deserError.getErrorType())
            .as("results[%d].errors[%d].errorType should survive round-trip", i, j)
            .isEqualTo(origError.getErrorType());

        assertThat(deserError.getLocation())
            .as("results[%d].errors[%d].location should survive round-trip", i, j)
            .isEqualTo(origError.getLocation());

        assertThat(deserError.getDescription())
            .as("results[%d].errors[%d].description should survive round-trip", i, j)
            .isEqualTo(origError.getDescription());

        assertThat(deserError.getSeverity())
            .as("results[%d].errors[%d].severity should survive round-trip", i, j)
            .isEqualTo(origError.getSeverity());
      }
    }
  }

  /**
   * Property 8: CSV 导出格式完整性
   *
   * <p>For any ScanReport containing N ScanResults, the exported CSV should contain exactly 1
   * header row plus the expected number of data rows (one per error for files with errors, one for
   * healthy files with no errors). Each line should have exactly 8 columns and the CSV should
   * comply with RFC 4180 (lines terminated by \r\n).
   *
   * <p><b>Validates: Requirements 4.2, 4.3</b>
   */
  @Property(tries = 100)
  @Label("Feature: tsfile-scan, Property 8: CSV 导出格式完整性")
  void csvExportShouldHaveCorrectFormatAndRowCount(@ForAll("scanReports") ScanReport report) {
    // Build a ScanTask from the report so we can use ScanService.exportReport
    ScanTask task = new ScanTask(report.getTaskId(), "/test/dir");
    task.setResults(report.getResults());

    // Create a Caffeine cache and put the task in it
    com.github.benmanes.caffeine.cache.Cache<String, ScanTask> cache =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10).build();
    cache.put(report.getTaskId(), task);

    // Create ScanService with the cache (other dependencies not needed for export)
    ScanService scanService = new ScanService(cache, null, null, null);

    // Export as CSV
    byte[] csvBytes = scanService.exportReport(report.getTaskId(), "csv");
    String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

    // Calculate expected data rows: for each ScanResult, max(1, errors.size())
    int expectedDataRows = 0;
    for (ScanResult result : report.getResults()) {
      expectedDataRows += Math.max(1, result.getErrors().size());
    }

    // Split by \r\n to get lines (the CSV ends with \r\n so the last split element is empty)
    String[] lines = csvContent.split("\r\n", -1);

    // The last element after split should be empty (trailing \r\n)
    assertThat(lines[lines.length - 1])
        .as("CSV should end with \\r\\n, leaving an empty trailing element after split")
        .isEmpty();

    // Actual content lines (excluding the trailing empty element)
    int totalContentLines = lines.length - 1;

    // Verify: total lines = 1 (header) + expectedDataRows
    assertThat(totalContentLines)
        .as(
            "CSV should have 1 header + %d data rows for %d ScanResults",
            expectedDataRows, report.getResults().size())
        .isEqualTo(1 + expectedDataRows);

    // Verify header row has exactly 8 columns
    String headerLine = lines[0];
    assertThat(headerLine)
        .as("Header should match expected CSV columns")
        .isEqualTo(
            "filePath,fileSize,healthStatus,errorType,severity,location,description,scanDurationMs");

    // Verify each data line has exactly 8 columns (accounting for RFC 4180 quoted fields)
    int expectedColumnCount = 8;
    for (int i = 1; i < totalContentLines; i++) {
      int columnCount = countCsvColumns(lines[i]);
      assertThat(columnCount)
          .as("Data line %d should have exactly %d columns", i, expectedColumnCount)
          .isEqualTo(expectedColumnCount);
    }

    // Verify RFC 4180 compliance: all line breaks within the content are \r\n
    // (no bare \n or \r outside of quoted fields)
    // We already verified the split by \r\n works correctly above.
    // Additionally verify the raw content only uses \r\n as line terminators
    // by checking that every \r is followed by \n and every \n is preceded by \r
    // (outside of quoted fields)
    assertRfc4180LineEndings(csvContent);
  }

  /**
   * Counts the number of columns in a CSV line, respecting RFC 4180 quoted fields. Fields enclosed
   * in double quotes may contain commas, which should not be counted as delimiters.
   */
  private int countCsvColumns(String line) {
    int count = 1;
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          // Escaped double quote inside quoted field — skip next char
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c == ',' && !inQuotes) {
        count++;
      }
    }
    return count;
  }

  /**
   * Verifies that the CSV content uses only \r\n line endings outside of quoted fields, as required
   * by RFC 4180.
   */
  private void assertRfc4180LineEndings(String content) {
    boolean inQuotes = false;
    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < content.length() && content.charAt(i + 1) == '"') {
          i++; // skip escaped quote
        } else {
          inQuotes = !inQuotes;
        }
      } else if (!inQuotes) {
        if (c == '\r') {
          assertThat(i + 1 < content.length() && content.charAt(i + 1) == '\n')
              .as("\\r at position %d outside quotes must be followed by \\n (RFC 4180)", i)
              .isTrue();
        } else if (c == '\n') {
          assertThat(i > 0 && content.charAt(i - 1) == '\r')
              .as("\\n at position %d outside quotes must be preceded by \\r (RFC 4180)", i)
              .isTrue();
        }
      }
    }
  }

  /**
   * Provides arbitrary {@link ScanReport} instances with random but valid field values. Each report
   * contains a random task ID, consistent counts, a random error type distribution, a list of
   * random ScanResults (each with random FileErrors), and a random total duration.
   */
  @Provide
  Arbitrary<ScanReport> scanReports() {
    return Combinators.combine(
            taskIds(),
            Arbitraries.longs().between(0, 600_000L), // totalDurationMs
            scanResultLists())
        .as((taskId, totalDurationMs, results) -> {
          ScanReport report = new ScanReport(taskId);
          report.setResults(results);
          report.setTotalFiles(results.size());
          report.setTotalDurationMs(totalDurationMs);

          // Compute counts from results
          int healthyCount = 0;
          int warningCount = 0;
          int errorCount = 0;
          Map<ScanErrorType, Integer> errorTypeDist = new HashMap<>();

          for (ScanResult result : results) {
            switch (result.getHealthStatus()) {
              case HEALTHY -> healthyCount++;
              case WARNING -> warningCount++;
              case ERROR -> errorCount++;
            }
            for (FileError error : result.getErrors()) {
              errorTypeDist.merge(error.getErrorType(), 1, Integer::sum);
            }
          }

          report.setHealthyCount(healthyCount);
          report.setWarningCount(warningCount);
          report.setErrorCount(errorCount);
          report.setErrorTypeDistribution(errorTypeDist);

          return report;
        });
  }

  /** Generates task IDs matching the format: 14-digit timestamp + hyphen + 6 alphanumeric. */
  private Arbitrary<String> taskIds() {
    Arbitrary<String> timestamp =
        Arbitraries.strings().numeric().ofLength(14); // 14-digit timestamp
    Arbitrary<String> randomPart =
        Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofLength(6); // 6 lowercase alphanumeric
    return Combinators.combine(timestamp, randomPart).as((ts, rnd) -> ts + "-" + rnd);
  }

  /** Generates a list of random {@link ScanResult} objects. */
  private Arbitrary<List<ScanResult>> scanResultLists() {
    return scanResults().list().ofMinSize(0).ofMaxSize(20);
  }

  /** Generates a single random {@link ScanResult} with random file errors. */
  private Arbitrary<ScanResult> scanResults() {
    return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30), // file name
            Arbitraries.longs().between(0, 10_000_000L), // file size
            Arbitraries.of(HealthStatus.values()), // health status
            fileErrorLists(), // errors
            Arbitraries.longs().between(0, 60_000L) // scan duration ms
            )
        .as((fileName, fileSize, healthStatus, errors, scanDurationMs) ->
            new ScanResult(
                "/data/" + fileName + ".tsfile", fileSize, healthStatus, errors, scanDurationMs));
  }

  /** Generates a list of random {@link FileError} objects. */
  private Arbitrary<List<FileError>> fileErrorLists() {
    return fileErrors().list().ofMinSize(0).ofMaxSize(5);
  }

  /** Generates a single random {@link FileError}. */
  private Arbitrary<FileError> fileErrors() {
    return Combinators.combine(
            Arbitraries.of(ScanErrorType.values()),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50), // location
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(100), // description
            Arbitraries.of(ErrorSeverity.values()))
        .as(FileError::new);
  }
}
