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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact, primitive-array index of chunk metadata keyed by chunk-header offset.
 *
 * <p>Replaces the former {@code Map<Long, IChunkMetadata>}, whose per-chunk cost is far larger than
 * the three fields the health check actually reads (a boxed {@code Long} key, a {@code HashMap}
 * node and a full {@code IChunkMetadata} carrying its statistics). This index packs those three
 * fields into primitive arrays: {@code 8 (offset) + 4 (measurement id) + 8 (start time) + 8 (end
 * time) = 28 bytes} per chunk, so the 1,838,571 chunks of a large real-world file need about 49 MiB
 * instead of several hundred MiB.
 *
 * <p>The marker-based traversal that consumes this index only ever moves forward through the file,
 * so a {@link Cursor} advances monotonically and the whole lookup pass costs O(n) rather than O(n
 * log n). A backward query — which traversal cannot produce — falls back to a binary search, so
 * correctness never depends on that ordering assumption.
 */
final class ChunkMetadataIndex {

  /** Signals that the configured chunk cap was exceeded while building the index. */
  static final class ChunkCountExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int limit;

    ChunkCountExceededException(int limit) {
      super("Chunk count exceeds the configured limit of " + limit);
      this.limit = limit;
    }

    int getLimit() {
      return limit;
    }
  }

  private static final int INITIAL_CAPACITY = 1024;

  /** Cap on the number of entries; adding one more throws {@link ChunkCountExceededException}. */
  private final int maxChunks;

  /** Distinct measurement names; chunk entries store an {@code int} index into this list. */
  private final List<String> measurementPool = new ArrayList<>();

  private final Map<String, Integer> measurementIdByMeasurement = new HashMap<>();

  private long[] offsets = new long[INITIAL_CAPACITY];
  private int[] measurementIds = new int[INITIAL_CAPACITY];
  private long[] startTimes = new long[INITIAL_CAPACITY];
  private long[] endTimes = new long[INITIAL_CAPACITY];
  private int size;
  private boolean sealed;

  ChunkMetadataIndex(int maxChunks) {
    this.maxChunks = maxChunks;
  }

  /**
   * Appends one chunk entry.
   *
   * @param offset offset of the chunk header (including its marker byte)
   * @param measurement measurement name owning the chunk
   * @param startTime first timestamp of the chunk
   * @param endTime last timestamp of the chunk
   * @throws ChunkCountExceededException if the index already holds {@code maxChunks} entries
   */
  void add(long offset, String measurement, long startTime, long endTime) {
    if (sealed) {
      throw new IllegalStateException("Chunk metadata index is already sealed");
    }
    if (size >= maxChunks) {
      throw new ChunkCountExceededException(maxChunks);
    }
    if (size == offsets.length) {
      grow();
    }
    offsets[size] = offset;
    measurementIds[size] = measurementId(measurement);
    startTimes[size] = startTime;
    endTimes[size] = endTime;
    size++;
  }

  int size() {
    return size;
  }

  boolean isEmpty() {
    return size == 0;
  }

  long offsetAt(int index) {
    return offsets[index];
  }

  String measurementAt(int index) {
    return measurementPool.get(measurementIds[index]);
  }

  long startTimeAt(int index) {
    return startTimes[index];
  }

  long endTimeAt(int index) {
    return endTimes[index];
  }

  /**
   * Sorts the entries by chunk offset and trims the backing arrays to the exact size.
   *
   * <p>Lookups require a sealed index.
   */
  void seal() {
    if (sealed) {
      return;
    }
    sortByOffset(0, size - 1);
    offsets = Arrays.copyOf(offsets, size);
    measurementIds = Arrays.copyOf(measurementIds, size);
    startTimes = Arrays.copyOf(startTimes, size);
    endTimes = Arrays.copyOf(endTimes, size);
    sealed = true;
  }

  /** Creates a forward-only lookup cursor; the returned cursor is cheap and single-pass. */
  Cursor cursor() {
    if (!sealed) {
      throw new IllegalStateException("seal() must be called before creating a cursor");
    }
    return new Cursor(this);
  }

  private int measurementId(String measurement) {
    Integer id = measurementIdByMeasurement.get(measurement);
    if (id == null) {
      id = measurementPool.size();
      measurementPool.add(measurement);
      measurementIdByMeasurement.put(measurement, id);
    }
    return id;
  }

  private void grow() {
    int newCapacity = offsets.length + (offsets.length >> 1) + 1;
    offsets = Arrays.copyOf(offsets, newCapacity);
    measurementIds = Arrays.copyOf(measurementIds, newCapacity);
    startTimes = Arrays.copyOf(startTimes, newCapacity);
    endTimes = Arrays.copyOf(endTimes, newCapacity);
  }

  private void sortByOffset(int low, int high) {
    int lo = low;
    int hi = high;
    while (lo < hi) {
      if (hi - lo < 16) {
        insertionSortByOffset(lo, hi);
        return;
      }
      long pivot = medianOfThree(lo, hi);
      int less = lo;
      int greater = hi;
      int current = lo;
      // Three-way partition: offsets are unique in practice, but equal keys must not loop forever.
      while (current <= greater) {
        if (offsets[current] < pivot) {
          swap(current, less);
          current++;
          less++;
        } else if (offsets[current] > pivot) {
          swap(current, greater);
          greater--;
        } else {
          current++;
        }
      }
      // Recurse into the smaller side only, keeping the stack depth logarithmic.
      if (less - lo < hi - greater) {
        sortByOffset(lo, less - 1);
        lo = greater + 1;
      } else {
        sortByOffset(greater + 1, hi);
        hi = less - 1;
      }
    }
  }

  private void insertionSortByOffset(int lo, int hi) {
    for (int i = lo + 1; i <= hi; i++) {
      int j = i;
      while (j > lo && offsets[j - 1] > offsets[j]) {
        swap(j - 1, j);
        j--;
      }
    }
  }

  private long medianOfThree(int lo, int hi) {
    int mid = lo + ((hi - lo) >>> 1);
    long a = offsets[lo];
    long b = offsets[mid];
    long c = offsets[hi];
    if (a < b) {
      return b < c ? b : Math.max(a, c);
    }
    return a < c ? a : Math.max(b, c);
  }

  private void swap(int i, int j) {
    if (i == j) {
      return;
    }
    long offset = offsets[i];
    offsets[i] = offsets[j];
    offsets[j] = offset;

    int measurementId = measurementIds[i];
    measurementIds[i] = measurementIds[j];
    measurementIds[j] = measurementId;

    long startTime = startTimes[i];
    startTimes[i] = startTimes[j];
    startTimes[j] = startTime;

    long endTime = endTimes[i];
    endTimes[i] = endTimes[j];
    endTimes[j] = endTime;
  }

  private int binarySearch(long offset) {
    int low = 0;
    int high = size - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      long midOffset = offsets[mid];
      if (midOffset < offset) {
        low = mid + 1;
      } else if (midOffset > offset) {
        high = mid - 1;
      } else {
        return mid;
      }
    }
    return -1;
  }

  /** Forward-only cursor over a sealed {@link ChunkMetadataIndex}. Not thread-safe. */
  static final class Cursor {

    private final ChunkMetadataIndex index;
    private int position;

    private Cursor(ChunkMetadataIndex index) {
      this.index = index;
    }

    /**
     * Returns the entry index whose chunk-header offset equals {@code offset}, or {@code -1} when
     * the chunk is not present in the index.
     *
     * <p>Offsets requested in ascending order cost O(1) amortized; a request that moves backwards
     * (impossible for file traversal) falls back to a binary search.
     */
    int find(long offset) {
      if (position > 0 && index.offsets[position - 1] >= offset) {
        return index.binarySearch(offset);
      }
      while (position < index.size && index.offsets[position] < offset) {
        position++;
      }
      if (position < index.size && index.offsets[position] == offset) {
        return position;
      }
      return -1;
    }

    String measurement(int index) {
      return this.index.measurementAt(index);
    }

    long startTime(int index) {
      return this.index.startTimeAt(index);
    }

    long endTime(int index) {
      return this.index.endTimeAt(index);
    }
  }
}
