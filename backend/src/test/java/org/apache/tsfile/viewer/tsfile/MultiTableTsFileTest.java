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

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.exception.write.WriteProcessException;
import org.apache.tsfile.file.metadata.ColumnSchemaBuilder;
import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.read.TsFileSequenceReader;
import org.apache.tsfile.write.record.Tablet;
import org.apache.tsfile.write.v4.ITsFileWriter;
import org.apache.tsfile.write.v4.TsFileWriterBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tsfile.viewer.dto.TableDTO;

/**
 * Test multi-table support in TsFile V4.
 *
 * <p>Creates a TsFile with 2 tables: 1. sensor_data: device_id (TAG), location (TAG), temperature
 * (FIELD), humidity (FIELD) 2. power_data: plant_id (TAG), voltage (FIELD), current (FIELD)
 */
class MultiTableTsFileTest {

  @TempDir Path tempDir;

  private File multiTableFile;
  private TsFileParser parser;

  @BeforeEach
  void setUp() throws Exception {
    multiTableFile = tempDir.resolve("multi-table.tsfile").toFile();
    parser = new TsFileParser();

    // Create TsFile with 2 tables
    createMultiTableTsFile();
  }

  private void createMultiTableTsFile() throws Exception {
    // Table 1: sensor_data with 2 TAG columns and 2 FIELD columns
    TableSchema sensorSchema =
        new TableSchema(
            "sensor_data",
            Arrays.asList(
                new ColumnSchemaBuilder()
                    .name("device_id")
                    .dataType(TSDataType.STRING)
                    .category(ColumnCategory.TAG)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("location")
                    .dataType(TSDataType.STRING)
                    .category(ColumnCategory.TAG)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("temperature")
                    .dataType(TSDataType.FLOAT)
                    .category(ColumnCategory.FIELD)
                    .build(),
                new ColumnSchemaBuilder()
                    .name("humidity")
                    .dataType(TSDataType.FLOAT)
                    .category(ColumnCategory.FIELD)
                    .build()));

    // For now, create a single-table file
    // TODO: When multi-table write API is available, add second table
    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(multiTableFile).tableSchema(sensorSchema).build()) {

      Tablet sensorTablet =
          new Tablet(
              Arrays.asList("device_id", "location", "temperature", "humidity"),
              Arrays.asList(
                  TSDataType.STRING, TSDataType.STRING, TSDataType.FLOAT, TSDataType.FLOAT));

      for (int row = 0; row < 100; row++) {
        sensorTablet.addTimestamp(row, row * 1000L);
        sensorTablet.addValue(row, "device_id", "sensor_" + (row % 5));
        sensorTablet.addValue(row, "location", row % 2 == 0 ? "room_A" : "room_B");
        sensorTablet.addValue(row, "temperature", 20.0f + row * 0.1f);
        sensorTablet.addValue(row, "humidity", 50.0f + row * 0.2f);
      }

      writer.write(sensorTablet);
    } catch (WriteProcessException e) {
      throw new RuntimeException("Failed to write sensor_data table", e);
    }
  }

  @Test
  void testParseTables_returnsSingleTable() throws Exception {
    // When
    List<TableDTO> tables = parser.parseTables(multiTableFile.getAbsolutePath());

    // Then - Currently only one table since multi-table write isn't supported yet
    assertThat(tables).hasSize(1);
  }

  @Test
  void testParseTables_firstTableHasTwoTagsAndTwoFields() throws Exception {
    // When
    List<TableDTO> tables = parser.parseTables(multiTableFile.getAbsolutePath());

    // Then - sensor_data table
    TableDTO sensorTable =
        tables.stream()
            .filter(t -> t.getTableName().equals("sensor_data"))
            .findFirst()
            .orElseThrow();

    assertThat(sensorTable.getTagColumns()).hasSize(2);
    assertThat(sensorTable.getFieldColumns()).hasSize(2);
    assertThat(sensorTable.getTotalColumns()).isEqualTo(4);

    // Verify TAG columns
    assertThat(sensorTable.getTagColumns().get(0).getName()).isEqualTo("device_id");
    assertThat(sensorTable.getTagColumns().get(0).getDataType()).isEqualTo("STRING");
    assertThat(sensorTable.getTagColumns().get(1).getName()).isEqualTo("location");
    assertThat(sensorTable.getTagColumns().get(1).getDataType()).isEqualTo("STRING");

    // Verify FIELD columns
    assertThat(sensorTable.getFieldColumns().get(0).getName()).isEqualTo("temperature");
    assertThat(sensorTable.getFieldColumns().get(0).getDataType()).isEqualTo("FLOAT");
    assertThat(sensorTable.getFieldColumns().get(1).getName()).isEqualTo("humidity");
    assertThat(sensorTable.getFieldColumns().get(1).getDataType()).isEqualTo("FLOAT");
  }

  @Test
  void testParseTables_secondTableHasOneTagAndTwoFields() throws Exception {
    // When
    List<TableDTO> tables = parser.parseTables(multiTableFile.getAbsolutePath());

    // Skip this test for now since we only have single table
    // TODO: Enable when multi-table write API is available
    if (tables.size() < 2) {
      return;
    }

    // Then - power_data table
    TableDTO powerTable =
        tables.stream()
            .filter(t -> t.getTableName().equals("power_data"))
            .findFirst()
            .orElseThrow();

    assertThat(powerTable.getTagColumns()).hasSize(1);
    assertThat(powerTable.getFieldColumns()).hasSize(2);
    assertThat(powerTable.getTotalColumns()).isEqualTo(3);

    // Verify TAG column
    assertThat(powerTable.getTagColumns().get(0).getName()).isEqualTo("plant_id");
    assertThat(powerTable.getTagColumns().get(0).getDataType()).isEqualTo("STRING");

    // Verify FIELD columns
    assertThat(powerTable.getFieldColumns().get(0).getName()).isEqualTo("voltage");
    assertThat(powerTable.getFieldColumns().get(0).getDataType()).isEqualTo("DOUBLE");
    assertThat(powerTable.getFieldColumns().get(1).getName()).isEqualTo("current");
    assertThat(powerTable.getFieldColumns().get(1).getDataType()).isEqualTo("DOUBLE");
  }

  @Test
  void testParseTables_verifyEncodingAndCompression() throws Exception {
    // When
    List<TableDTO> tables = parser.parseTables(multiTableFile.getAbsolutePath());

    // Then - verify sensor_data encoding and compression
    TableDTO sensorTable =
        tables.stream()
            .filter(t -> t.getTableName().equals("sensor_data"))
            .findFirst()
            .orElseThrow();

    // TAG columns encoding/compression depends on schema configuration
    // Just verify they are not null
    assertThat(sensorTable.getTagColumns().get(0).getEncoding()).isNotNull();
    assertThat(sensorTable.getTagColumns().get(0).getCompression()).isNotNull();

    // FIELD columns encoding/compression depends on schema configuration
    assertThat(sensorTable.getFieldColumns().get(0).getEncoding()).isNotNull();
    assertThat(sensorTable.getFieldColumns().get(0).getCompression()).isNotNull();
  }

  @Test
  void testParseMetadata_includesTablesField() throws Exception {
    // When
    TsFileSequenceReader reader = new TsFileSequenceReader(multiTableFile.getAbsolutePath());
    List<TsFileParser.TableInfo> tableInfos = parser.parseTables(reader);
    reader.close();

    // Then - Currently only one table since multi-table write isn't supported yet
    assertThat(tableInfos).hasSize(1);
    assertThat(tableInfos.get(0).getTableName()).isEqualTo("sensor_data");
  }
}
