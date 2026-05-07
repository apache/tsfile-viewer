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

package org.apache.tsfile.viewer.dto;

/**
 * DTO representing a RowGroup in a TsFile.
 *
 * <p>Contains metadata about a RowGroup including its index, device, time range, and chunk count.
 *
 * <p>Validates: Requirement 2.4 (RowGroup table)
 */
public class RowGroupDTO {

  private int index;
  private String device;
  private long startTime;
  private long endTime;
  private int chunkCount;

  /** Default constructor for JSON deserialization. */
  public RowGroupDTO() {}

  /**
   * Creates a new RowGroup DTO.
   *
   * @param index RowGroup index within the file
   * @param device device identifier
   * @param startTime start timestamp of the RowGroup
   * @param endTime end timestamp of the RowGroup
   * @param chunkCount number of chunks in this RowGroup
   */
  public RowGroupDTO(int index, String device, long startTime, long endTime, int chunkCount) {
    this.index = index;
    this.device = device;
    this.startTime = startTime;
    this.endTime = endTime;
    this.chunkCount = chunkCount;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public String getDevice() {
    return device;
  }

  public void setDevice(String device) {
    this.device = device;
  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }

  public int getChunkCount() {
    return chunkCount;
  }

  public void setChunkCount(int chunkCount) {
    this.chunkCount = chunkCount;
  }

  /** Builder class for creating RowGroupDTO instances. */
  public static class Builder {
    private int index;
    private String device;
    private long startTime;
    private long endTime;
    private int chunkCount;

    public Builder index(int index) {
      this.index = index;
      return this;
    }

    public Builder device(String device) {
      this.device = device;
      return this;
    }

    public Builder startTime(long startTime) {
      this.startTime = startTime;
      return this;
    }

    public Builder endTime(long endTime) {
      this.endTime = endTime;
      return this;
    }

    public Builder chunkCount(int chunkCount) {
      this.chunkCount = chunkCount;
      return this;
    }

    public RowGroupDTO build() {
      return new RowGroupDTO(index, device, startTime, endTime, chunkCount);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
