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

import java.util.List;

import org.apache.tsfile.file.metadata.IDeviceID;

/**
 * Immutable per-file index of devices and their chunk counts.
 *
 * <p>Holds only device names and an int prefix-sum array, so it stays small (a few MB even for
 * ~100k devices) and never contains chunk or timeseries metadata. Every page request is served from
 * this index plus per-device reads, which is what keeps paging incremental.
 *
 * <p>The {@code lastModified}/{@code size} pair identifies the file version the index was built
 * from; {@link #matches(long, long)} is used to detect a rewritten file.
 */
public final class DeviceIndex {

  private final long lastModified;
  private final long size;
  private final List<IDeviceID> devices;
  private final int[] chunkPrefix;

  private DeviceIndex(
      long lastModified, long size, List<IDeviceID> devices, int[] chunkPrefix) {
    this.lastModified = lastModified;
    this.size = size;
    this.devices = devices;
    this.chunkPrefix = chunkPrefix;
  }

  /**
   * Creates an index from a device list and the chunk count of each device.
   *
   * @param lastModified file modification time the index was built from
   * @param size file size the index was built from
   * @param devices devices in file order
   * @param chunkCounts chunk count per device, index-aligned with {@code devices}
   * @return device index
   */
  public static DeviceIndex of(
      long lastModified, long size, List<IDeviceID> devices, int[] chunkCounts) {
    if (devices.size() != chunkCounts.length) {
      throw new IllegalArgumentException(
          "devices and chunkCounts must be index-aligned: "
              + devices.size()
              + " vs "
              + chunkCounts.length);
    }
    int[] prefix = new int[devices.size() + 1];
    for (int i = 0; i < chunkCounts.length; i++) {
      prefix[i + 1] = prefix[i] + Math.max(chunkCounts[i], 0);
    }
    return new DeviceIndex(lastModified, size, List.copyOf(devices), prefix);
  }

  /**
   * Tells whether this index still describes the file's current version.
   *
   * @param lastModified current file modification time
   * @param size current file size
   * @return true when the index was built from that exact version
   */
  public boolean matches(long lastModified, long size) {
    return this.lastModified == lastModified && this.size == size;
  }

  /** Number of devices, which is also the number of RowGroups of this file. */
  public int deviceCount() {
    return devices.size();
  }

  /** Devices in file order. */
  public List<IDeviceID> devices() {
    return devices;
  }

  /** Total number of chunks in the file. */
  public int chunkCount() {
    return chunkPrefix[devices.size()];
  }

  /**
   * Number of chunks that precede the given device in file order.
   *
   * @param devicePosition position of the device in {@link #devices()}
   * @return absolute index of that device's first chunk
   */
  public int chunksBeforeDevice(int devicePosition) {
    return chunkPrefix[devicePosition];
  }

  /**
   * Number of chunks of the given device.
   *
   * @param devicePosition position of the device in {@link #devices()}
   * @return chunk count of that device
   */
  public int chunkCountOf(int devicePosition) {
    return chunkPrefix[devicePosition + 1] - chunkPrefix[devicePosition];
  }

  /**
   * Finds the device that owns the chunk at the given absolute position, so a chunk page can start
   * reading at exactly the device it needs instead of from the first device.
   *
   * @param chunkOffset absolute chunk position
   * @return position of the owning device, or {@link #deviceCount()} when the offset is past the
   *     last chunk
   */
  public int deviceIndexForChunk(int chunkOffset) {
    int low = 0;
    int high = devices.size() - 1;
    int result = devices.size();
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (chunkPrefix[mid + 1] > chunkOffset) {
        result = mid;
        high = mid - 1;
      } else {
        low = mid + 1;
      }
    }
    return result;
  }

  /** File modification time this index was built from. */
  public long lastModified() {
    return lastModified;
  }

  /** File size this index was built from. */
  public long size() {
    return size;
  }
}
