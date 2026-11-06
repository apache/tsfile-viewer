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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tsfile.file.metadata.IChunkMetadata;
import org.apache.tsfile.file.metadata.TimeseriesMetadata;
import org.apache.tsfile.read.TsFileSequenceReader;

import org.apache.tsfile.viewer.dto.ErrorSeverity;
import org.apache.tsfile.viewer.dto.HealthStatus;
import org.apache.tsfile.viewer.dto.ScanErrorType;
import org.apache.tsfile.viewer.service.FileError;
import org.apache.tsfile.viewer.service.ScanResult;

/**
 * Tests for the compact chunk metadata index that replaced the per-chunk {@code Map<Long,
 * IChunkMetadata>} in {@link TsFileHealthChecker}.
 *
 * <p>Covers: structure construction and lookup correctness, {@code quickCheck} early exit, the
 * over-limit degradation path, and equality of the scan conclusions against the pre-change
 * metadata map.
 */
class TsFileHealthCheckerCompactIndexTest {

  private static void assertEntry(
      ChunkMetadataIndex index,
      int position,
      long offset,
      String measurement,
      long startTime,
      long endTime) {
    assertThat(index.offsetAt(position)).isEqualTo(offset);
    assertThat(index.measurementAt(position)).isEqualTo(measurement);
    assertThat(index.startTimeAt(position)).isEqualTo(startTime);
    assertThat(index.endTimeAt(position)).isEqualTo(endTime);
  }

  // -------------------------------------------------------------------------
  // Compact structure: construction and lookups
  // -------------------------------------------------------------------------

  @Nested
  class CompactIndexTests {

    @Test
    void seal_sortsByOffsetAndKeepsPayloadAttached() {
      ChunkMetadataIndex index = new ChunkMetadataIndex(100);
      index.add(300L, "s3", 30L, 39L);
      index.add(100L, "s1", 10L, 19L);
      index.add(200L, "s2", 20L, 29L);

      index.seal();

      assertThat(index.size()).isEqualTo(3);
      assertEntry(index, 0, 100L, "s1", 10L, 19L);
      assertEntry(index, 1, 200L, "s2", 20L, 29L);
      assertEntry(index, 2, 300L, "s3", 30L, 39L);
    }

    @Test
    void cursor_forwardLookupsMatchEveryEntry() {
      ChunkMetadataIndex index = new ChunkMetadataIndex(100);
      index.add(20L, "b", 200L, 209L);
      index.add(10L, "a", 100L, 109L);
      index.add(30L, "a", 300L, 309L);
      index.seal();

      ChunkMetadataIndex.Cursor cursor = index.cursor();

      for (int position = 0; position < index.size(); position++) {
        int found = cursor.find(index.offsetAt(position));
        assertThat(found).isEqualTo(position);
        assertThat(cursor.measurement(found)).isEqualTo(index.measurementAt(position));
        assertThat(cursor.startTime(found)).isEqualTo(index.startTimeAt(position));
        assertThat(cursor.endTime(found)).isEqualTo(index.endTimeAt(position));
      }
    }

    @Test
    void cursor_returnsMinusOneForAbsentOffsetsAndKeepsAdvancing() {
      ChunkMetadataIndex index = new ChunkMetadataIndex(100);
      index.add(100L, "a", 1L, 2L);
      index.add(200L, "a", 3L, 4L);
      index.add(300L, "a", 5L, 6L);
      index.seal();

      ChunkMetadataIndex.Cursor cursor = index.cursor();

      assertThat(cursor.find(50L)).isEqualTo(-1);
      assertThat(cursor.find(150L)).isEqualTo(-1);
      assertThat(cursor.find(200L)).isEqualTo(1);
      assertThat(cursor.find(250L)).isEqualTo(-1);
      assertThat(cursor.find(350L)).isEqualTo(-1);
      assertThat(cursor.find(300L)).isEqualTo(2);
    }

    @Test
    void cursor_backwardLookupFallsBackToBinarySearch() {
      ChunkMetadataIndex index = new ChunkMetadataIndex(100);
      for (int i = 0; i < 40; i++) {
        index.add(i * 10L, "m" + (i % 3), i, i);
      }
      index.seal();

      ChunkMetadataIndex.Cursor cursor = index.cursor();

      assertThat(cursor.find(390L)).isEqualTo(39);
      // Traversal never asks for a smaller offset, but the answer must still be correct.
      assertThat(cursor.find(10L)).isEqualTo(1);
      assertThat(cursor.find(390L)).isEqualTo(39);
    }

    @Test
    void seal_sortsLargeOutOfOrderInputBeyondInitialCapacity() {
      int entryCount = 5_000;
      ChunkMetadataIndex index = new ChunkMetadataIndex(entryCount);
      Map<Integer, Integer> sourceIndexByOffset = new HashMap<>();
      // Deterministic shuffle so entries arrive far out of offset order.
      for (int i = 0; i < entryCount; i++) {
        int offset = (i * 7919) % entryCount;
        // Payload is derived from the source index, so it must follow the entry through the sort.
        index.add(offset, "s" + (i % 7), i * 1000L, i * 1000L + 999L);
        sourceIndexByOffset.put(offset, i);
      }

      index.seal();

      assertThat(index.size()).isEqualTo(entryCount);
      ChunkMetadataIndex.Cursor cursor = index.cursor();
      for (int offset = 0; offset < entryCount; offset++) {
        int found = cursor.find(offset);
        assertThat(found).as("offset %s must be indexed", offset).isGreaterThanOrEqualTo(0);
        assertThat(index.offsetAt(found)).isEqualTo(offset);

        int sourceIndex = sourceIndexByOffset.get(offset);
        assertThat(index.measurementAt(found)).isEqualTo("s" + (sourceIndex % 7));
        assertThat(index.startTimeAt(found)).isEqualTo(sourceIndex * 1000L);
        assertThat(index.endTimeAt(found)).isEqualTo(sourceIndex * 1000L + 999L);
      }
    }

    @Test
    void seal_toleratesDuplicateOffsets() {
      ChunkMetadataIndex index = new ChunkMetadataIndex(100);
      for (int i = 0; i < 40; i++) {
        index.add(7L, "dup" + i, i, i);
      }

      index.seal();

      assertThat(index.size()).isEqualTo(40);
      assertThat(index.cursor().find(7L)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void add_beyondMaxChunksThrowsWithLimit() {
      ChunkMetadataIndex index = new ChunkMetadataIndex(2);
      index.add(1L, "a", 0L, 0L);
      index.add(2L, "a", 0L, 0L);

      assertThatThrownBy(() -> index.add(3L, "a", 0L, 0L))
          .isInstanceOf(ChunkMetadataIndex.ChunkCountExceededException.class)
          .hasMessageContaining("2");
    }

    @Test
    void emptyIndexHasNoEntriesAndFindsNothing() {
      ChunkMetadataIndex index = new ChunkMetadataIndex(10);
      index.seal();

      assertThat(index.isEmpty()).isTrue();
      assertThat(index.cursor().find(0L)).isEqualTo(-1);
    }

    @Test
    void add_afterSealAndCursorBeforeSealAreRejected() {
      ChunkMetadataIndex index = new ChunkMetadataIndex(10);
      index.add(1L, "a", 0L, 5L);

      assertThatThrownBy(index::cursor).isInstanceOf(IllegalStateException.class);

      index.seal();
      assertThatThrownBy(() -> index.add(2L, "b", 6L, 9L))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  // -------------------------------------------------------------------------
  // Checker integration: equivalence, degradation, early exit
  // -------------------------------------------------------------------------

  @Nested
  class CheckerIntegrationTests {

    private static final List<String> DEVICE_IDS = List.of("d1", "d2", "d3");

    private Path createSampleFile(Path tempDir) throws Exception {
      return TsFileTestUtils.createTableModelMultipleDevices(
              tempDir.resolve("compact-index-sample.tsfile"), "wind", DEVICE_IDS, 20)
          .toPath();
    }

    /** The pre-change implementation: one map entry per chunk, built from all metadata at once. */
    private Map<Long, IChunkMetadata> legacyChunkMetadataMap(Path file) throws IOException {
      Map<Long, IChunkMetadata> offsetToChunkMetadata = new HashMap<>();
      try (TsFileSequenceReader reader = new TsFileSequenceReader(file.toString())) {
        for (List<TimeseriesMetadata> timeseriesMetadataList :
            reader.getAllTimeseriesMetadata(true).values()) {
          for (TimeseriesMetadata timeseriesMetadata : timeseriesMetadataList) {
            for (IChunkMetadata chunkMetadata : timeseriesMetadata.getChunkMetadataList()) {
              offsetToChunkMetadata.put(chunkMetadata.getOffsetOfChunkHeader(), chunkMetadata);
            }
          }
        }
      }
      return offsetToChunkMetadata;
    }

    @Test
    void compactIndex_matchesLegacyMetadataMap_sameFileSameConclusion(@TempDir Path tempDir)
        throws Exception {
      Path file = createSampleFile(tempDir);

      Map<Long, IChunkMetadata> legacyMap = legacyChunkMetadataMap(file);
      assertThat(legacyMap).as("the generated file must contain chunks").isNotEmpty();

      TsFileHealthChecker checker = new TsFileHealthChecker();
      List<Long> ascendingOffsets = new ArrayList<>(legacyMap.keySet());
      ascendingOffsets.sort(null);

      try (TsFileSequenceReader reader = new TsFileSequenceReader(file.toString())) {
        ChunkMetadataIndex compactIndex = checker.buildChunkMetadataIndex(reader);

        assertThat(compactIndex.isEmpty()).isEqualTo(legacyMap.isEmpty());
        assertThat(compactIndex.size()).isEqualTo(legacyMap.size());

        ChunkMetadataIndex.Cursor cursor = compactIndex.cursor();
        for (Long offset : ascendingOffsets) {
          int found = cursor.find(offset);
          assertThat(found).as("offset %s must be indexed", offset).isGreaterThanOrEqualTo(0);
          IChunkMetadata expected = legacyMap.get(offset);
          assertThat(cursor.measurement(found)).isEqualTo(expected.getMeasurementUid());
          assertThat(cursor.startTime(found)).isEqualTo(expected.getStartTime());
          assertThat(cursor.endTime(found)).isEqualTo(expected.getEndTime());
        }
      }

      // Below the cap the conclusion is unchanged: data-area traversal runs, no skip marker.
      ScanResult result = checker.check(file);
      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
      assertThat(result.getErrors()).isEmpty();
      assertThat(result.getFilePath()).isEqualTo(file.toString());
      assertThat(result.getFileSize()).isGreaterThan(0);
    }

    @Test
    void check_overChunkLimit_degradesAndReportsSkip(@TempDir Path tempDir) throws Exception {
      Path file = createSampleFile(tempDir);
      int chunkCount = legacyChunkMetadataMap(file).size();
      assertThat(chunkCount).isGreaterThan(1);

      ScanResult result = new TsFileHealthChecker(1).check(file);

      assertThat(result.getHealthStatus()).isEqualTo(HealthStatus.WARNING);
      assertThat(result.getErrors()).hasSize(1);
      FileError skipError = result.getErrors().get(0);
      assertThat(skipError.getErrorType()).isEqualTo(ScanErrorType.DATA_VALIDATION_SKIPPED);
      assertThat(skipError.getSeverity()).isEqualTo(ErrorSeverity.WARNING);
      assertThat(skipError.getDescription()).contains("max-chunks").contains("skipped");
      assertThat(result.getFilePath()).isEqualTo(file.toString());
      assertThat(result.getFileSize()).isGreaterThan(0);
      assertThat(result.getScanDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void quickCheck_stopsAtFirstChunk(@TempDir Path tempDir) throws Exception {
      Path file = createSampleFile(tempDir);
      int chunkCount = legacyChunkMetadataMap(file).size();
      assertThat(chunkCount).isGreaterThan(1);

      TsFileHealthChecker oneChunkChecker = new TsFileHealthChecker(1);
      ScanResult quickResult = oneChunkChecker.quickCheck(file);
      assertThat(quickResult.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
      assertThat(quickResult.getErrors()).isEmpty();

      // The same cap makes the full check degrade, which proves the file holds more than one chunk
      // and that quickCheck answered without indexing all of them.
      ScanResult fullResult = oneChunkChecker.check(file);
      assertThat(fullResult.getErrors())
          .extracting(FileError::getErrorType)
          .containsExactly(ScanErrorType.DATA_VALIDATION_SKIPPED);
    }
  }
}
