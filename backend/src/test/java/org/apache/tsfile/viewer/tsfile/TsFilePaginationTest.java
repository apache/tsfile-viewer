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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.file.metadata.ColumnSchema;
import org.apache.tsfile.file.metadata.ColumnSchemaBuilder;
import org.apache.tsfile.file.metadata.TableSchema;
import org.apache.tsfile.read.TsFileSequenceReader;
import org.apache.tsfile.write.record.Tablet;
import org.apache.tsfile.write.v4.ITsFileWriter;
import org.apache.tsfile.write.v4.TsFileWriterBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tsfile.viewer.dto.ChunkDTO;
import org.apache.tsfile.viewer.dto.PageDTO;
import org.apache.tsfile.viewer.dto.RowGroupDTO;

/**
 * Tests for incremental RowGroup/Chunk paging against a real TsFile.
 *
 * <p>Checks that device-based pages tile the full metadata exactly, that a chunk page crossing a
 * device boundary is stitched correctly, and that offsets past the end return an empty page.
 *
 * <p>Validates: pagination boundaries plus the "no full materialization" paging contract.
 */
class TsFilePaginationTest {

  private static final int DEVICES = 4;
  private static final int MEASUREMENTS = 3;
  private static final int ROWS = 10;

  @TempDir Path tempDir;

  private final TsFileParser parser = new TsFileParser();

  private Path tsFile;

  @BeforeEach
  void setUp() throws Exception {
    tsFile = createTableModelTsFile(tempDir.resolve("pagination.tsfile"), DEVICES, MEASUREMENTS, ROWS);
  }

  @Test
  @DisplayName("Device index totals match the full enumeration of the file")
  void shouldMatchFullEnumeration() throws Exception {
    DeviceIndex index = parser.parseDeviceIndex(tsFile.toString());
    List<RowGroupDTO> allRowGroups = parser.parseRowGroups(tsFile.toString());
    List<ChunkDTO> allChunks = parser.parseChunks(tsFile.toString());

    System.out.printf(
        "[index] devices=%d chunks=%d chunksPerDevice=%d enumeratedRowGroups=%d enumeratedChunks=%d%n",
        index.deviceCount(),
        index.chunkCount(),
        index.chunkCountOf(0),
        allRowGroups.size(),
        allChunks.size());

    assertThat(index.deviceCount()).isEqualTo(DEVICES).isEqualTo(allRowGroups.size());
    assertThat(index.chunkCount()).isEqualTo(allChunks.size());
    for (int position = 0; position < index.deviceCount(); position++) {
      assertThat(index.chunkCountOf(position)).isEqualTo(allRowGroups.get(position).getChunkCount());
    }
  }

  @Test
  @DisplayName("Device index is stamped with the file version it was built from")
  void shouldStampDeviceIndexWithFileVersion() throws Exception {
    DeviceIndex index = parser.parseDeviceIndex(tsFile.toString());

    long lastModified = Files.getLastModifiedTime(tsFile).toMillis();
    long size = Files.size(tsFile);

    assertThat(index.matches(lastModified, size)).isTrue();
    assertThat(index.matches(lastModified + 1, size)).isFalse();
    assertThat(index.matches(lastModified, size + 1)).isFalse();
  }

  @Test
  @DisplayName("offset=0 returns the first page")
  void shouldReturnFirstRowGroupPage() throws Exception {
    List<RowGroupDTO> all = parser.parseRowGroups(tsFile.toString());

    try (TsFileSequenceReader reader = new TsFileSequenceReader(tsFile.toString())) {
      List<RowGroupDTO> page = parser.readRowGroupPage(reader, index(), 0, 2);

      assertThat(page).hasSize(2);
      assertThat(page.get(0).getIndex()).isZero();
      assertThat(page.get(0).getDevice()).isEqualTo(all.get(0).getDevice());
      assertThat(page.get(1).getDevice()).isEqualTo(all.get(1).getDevice());
    }
  }

  @Test
  @DisplayName("RowGroup pages tile the whole list without gaps or duplicates")
  void shouldTileEveryRowGroup() throws Exception {
    List<RowGroupDTO> all = parser.parseRowGroups(tsFile.toString());

    try (TsFileSequenceReader reader = new TsFileSequenceReader(tsFile.toString())) {
      DeviceIndex index = index();
      List<RowGroupDTO> collected = new ArrayList<>();

      int offset = 0;
      List<PageDTO<RowGroupDTO>> pages = new ArrayList<>();
      while (offset < index.deviceCount()) {
        List<RowGroupDTO> page = parser.readRowGroupPage(reader, index, offset, 2);
        assertThat(page).isNotEmpty();
        pages.add(PageDTO.of(page, index.deviceCount(), offset, 2));
        collected.addAll(page);
        offset += page.size();
      }

      assertThat(collected)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyElementsOf(all);
      assertThat(pages).isNotEmpty();
      assertThat(pages.get(pages.size() - 1).isHasMore()).isFalse();
      if (pages.size() > 1) {
        assertThat(pages.get(0).isHasMore()).isTrue();
      }
    }
  }

  @Test
  @DisplayName("RowGroup offset beyond total returns an empty list")
  void shouldReturnEmptyRowGroupPageBeyondTotal() throws Exception {
    try (TsFileSequenceReader reader = new TsFileSequenceReader(tsFile.toString())) {
      DeviceIndex index = index();

      assertThat(parser.readRowGroupPage(reader, index, index.deviceCount(), 50)).isEmpty();
      assertThat(parser.readRowGroupPage(reader, index, 10_000, 50)).isEmpty();
      assertThat(parser.readRowGroupPage(reader, index, Integer.MAX_VALUE, 200)).isEmpty();
    }
  }

  @Test
  @DisplayName("Chunk pages tile the whole list and cross device boundaries correctly")
  void shouldTileEveryChunkIncludingDeviceBoundaries() throws Exception {
    List<ChunkDTO> all = parser.parseChunks(tsFile.toString());

    try (TsFileSequenceReader reader = new TsFileSequenceReader(tsFile.toString())) {
      DeviceIndex index = index();
      assertThat(index.chunkCount()).isEqualTo(all.size());

      int chunksPerDevice = index.chunkCountOf(0);
      assertThat(chunksPerDevice).isPositive();

      // A page starting on the last chunk of a device and ending inside the next one.
      int crossing = chunksPerDevice - 1;
      List<ChunkDTO> crossingPage = parser.readChunkPage(reader, index, crossing, 3);
      assertThat(crossingPage).hasSize(3);
      assertThat(crossingPage)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyElementsOf(all.subList(crossing, crossing + 3));
      assertThat(crossingPage.get(0).getDevice()).isNotEqualTo(crossingPage.get(2).getDevice());

      // A page starting exactly on a device boundary.
      List<ChunkDTO> boundaryPage = parser.readChunkPage(reader, index, chunksPerDevice, 4);
      assertThat(boundaryPage)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyElementsOf(all.subList(chunksPerDevice, chunksPerDevice + 4));

      // A page aligned inside a single device.
      List<ChunkDTO> innerPage = parser.readChunkPage(reader, index, 1, 2);
      assertThat(innerPage)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyElementsOf(all.subList(1, 3));

      // A trailing partial page.
      List<ChunkDTO> tailPage = parser.readChunkPage(reader, index, all.size() - 2, 50);
      assertThat(tailPage)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyElementsOf(all.subList(all.size() - 2, all.size()));
    }
  }

  @Test
  @DisplayName("Chunk pages tile the whole list with a non-dividing page size")
  void shouldTileAllChunksWithSmallPages() throws Exception {
    List<ChunkDTO> all = parser.parseChunks(tsFile.toString());

    try (TsFileSequenceReader reader = new TsFileSequenceReader(tsFile.toString())) {
      DeviceIndex index = index();
      List<ChunkDTO> collected = new ArrayList<>();

      int offset = 0;
      while (offset < index.chunkCount()) {
        List<ChunkDTO> page = parser.readChunkPage(reader, index, offset, 2);
        assertThat(page).isNotEmpty();
        collected.addAll(page);
        offset += page.size();
      }

      assertThat(collected)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyElementsOf(all);
    }
  }

  @Test
  @DisplayName("Chunk offset beyond total returns an empty list")
  void shouldReturnEmptyChunkPageBeyondTotal() throws Exception {
    try (TsFileSequenceReader reader = new TsFileSequenceReader(tsFile.toString())) {
      DeviceIndex index = index();

      assertThat(parser.readChunkPage(reader, index, index.chunkCount(), 50)).isEmpty();
      assertThat(parser.readChunkPage(reader, index, 10_000, 50)).isEmpty();
      assertThat(parser.readChunkPage(reader, index, Integer.MAX_VALUE, 200)).isEmpty();
    }
  }

  @Test
  @DisplayName("Summary reports counts but never carries RowGroup or Chunk lists")
  void shouldNotBuildDetailListsInSummary() throws Exception {
    TsFileParser.SummaryMetadata summary = parser.parseSummary(tsFile.toString());

    int enumeratedChunks = parser.parseChunks(tsFile.toString()).size();

    System.out.printf(
        "[summary] devices=%d measurements=%d measurementCount=%d chunks=%d%n",
        summary.basic().deviceCount(),
        summary.measurements().size(),
        summary.basic().measurementCount(),
        summary.basic().chunkCount());

    assertThat(summary.basic().deviceCount()).isEqualTo(DEVICES);
    assertThat(summary.basic().rowGroupCount()).isEqualTo(summary.deviceIndex().deviceCount());
    assertThat(summary.basic().chunkCount()).isEqualTo(summary.deviceIndex().chunkCount());
    assertThat(summary.basic().chunkCount()).isEqualTo(enumeratedChunks);
    assertThat(summary.deviceIndex().deviceCount()).isEqualTo(DEVICES);
    assertThat(summary.measurements()).isNotEmpty();
    assertThat(summary.basic().measurementCount()).isPositive();
  }

  private DeviceIndex index() throws IOException {
    return parser.parseDeviceIndex(tsFile.toString());
  }

  private static Path createTableModelTsFile(
      Path filePath, int numDevices, int numMeasurements, int numRows) throws Exception {
    Files.deleteIfExists(filePath);
    File file = filePath.toFile();

    List<String> columnNames = new ArrayList<>();
    List<TSDataType> dataTypes = new ArrayList<>();
    List<ColumnSchema> columnSchemas = new ArrayList<>();

    columnNames.add("device_id");
    dataTypes.add(TSDataType.STRING);
    columnSchemas.add(
        new ColumnSchemaBuilder()
            .name("device_id")
            .dataType(TSDataType.STRING)
            .category(ColumnCategory.TAG)
            .build());

    for (int m = 0; m < numMeasurements; m++) {
      columnNames.add("s" + m);
      dataTypes.add(TSDataType.INT64);
      columnSchemas.add(
          new ColumnSchemaBuilder()
              .name("s" + m)
              .dataType(TSDataType.INT64)
              .category(ColumnCategory.FIELD)
              .build());
    }

    TableSchema tableSchema = new TableSchema("test_table", columnSchemas);

    try (ITsFileWriter writer =
        new TsFileWriterBuilder().file(file).tableSchema(tableSchema).build()) {
      for (int d = 0; d < numDevices; d++) {
        Tablet tablet = new Tablet(columnNames, dataTypes);
        for (int row = 0; row < numRows; row++) {
          tablet.addTimestamp(row, d * 1_000_000L + row);
          tablet.addValue(row, "device_id", "device_" + d);
          for (int m = 0; m < numMeasurements; m++) {
            tablet.addValue(row, "s" + m, (long) (d * 1000 + m * 100 + row));
          }
        }
        writer.write(tablet);
      }
    }

    return filePath;
  }
}
