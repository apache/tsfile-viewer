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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.dto.ChartDataRequest;
import org.apache.tsfile.viewer.dto.ChartDataResponse;
import org.apache.tsfile.viewer.tsfile.TsFileDataReader;
import org.apache.tsfile.viewer.tsfile.TsFileTestUtils;

/**
 * Tests that building chart series keeps every plottable field.
 *
 * <p>Regression cover for boolean-only tables: the chart used to come back empty (no series) while
 * the data preview listed the rows, because booleans were not converted into numbers.
 */
class DataServiceChartTest {

  private static final String TABLE_NAME = "fault_table";
  private static final long START_TIME_NS = 1_719_731_133_000_000_000L;
  private static final long INTERVAL_NS = 1_000_000_000L;
  private static final int POINT_COUNT = 10;

  private Path tempDir;
  private DataService dataService;
  private String fileId;

  @BeforeEach
  void setUp() throws Exception {
    tempDir = Files.createTempDirectory("data-service-chart-test");

    File file =
        TsFileTestUtils.createTableModelBooleanField(
            tempDir.resolve("boolean-field.tsfile"),
            TABLE_NAME,
            "C919",
            START_TIME_NS,
            INTERVAL_NS,
            POINT_COUNT);

    TsFileProperties properties = new TsFileProperties();
    properties.getAllowedDirectories().add(tempDir.toString());
    properties.getQuery().setTimeoutSeconds(30);

    FileService fileService =
        new FileService(properties, new PathValidationService(properties));
    dataService = new DataService(properties, fileService, new TsFileDataReader());
    fileId = fileService.registerFile(file.getAbsolutePath());
  }

  @AfterEach
  void tearDown() throws Exception {
    if (tempDir == null || !Files.exists(tempDir)) {
      return;
    }
    Files.walk(tempDir)
        .sorted((a, b) -> -a.compareTo(b))
        .forEach(
            path -> {
              try {
                Files.deleteIfExists(path);
              } catch (Exception e) {
                // Ignore cleanup errors
              }
            });
  }

  @Test
  @DisplayName("Should build a series for a boolean field, mapping false/true to 0/1")
  void shouldBuildSeriesForBooleanField() throws Exception {
    ChartDataRequest request = new ChartDataRequest();
    request.setFileId(fileId);
    request.setTableName(TABLE_NAME);
    request.setMeasurements(List.of("value"));
    request.setMaxPoints(100);

    ChartDataResponse response = dataService.queryChartData(request);

    assertThat(response.getSeries()).hasSize(1);
    List<double[]> points = response.getSeries().get(0).getData();
    assertThat(points).hasSize(POINT_COUNT);
    assertThat(response.getTotalPoints()).isEqualTo(POINT_COUNT);

    assertThat(points.get(0)[0]).isEqualTo((double) START_TIME_NS);
    assertThat(points.get(0)[1]).isEqualTo(0.0d);
    assertThat(points.get(POINT_COUNT - 1)[1]).isEqualTo(1.0d);
    assertThat(points).allSatisfy(point -> assertThat(point[1]).isIn(0.0d, 1.0d));
  }
}
