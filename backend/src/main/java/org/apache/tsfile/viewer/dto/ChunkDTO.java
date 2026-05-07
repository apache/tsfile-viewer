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
 * DTO representing a Chunk in a TsFile.
 *
 * <p>Contains metadata about a Chunk including its measurement, offset, size, and compression
 * ratio.
 *
 * <p>Validates: Requirement 2.5 (Chunk table)
 */
public class ChunkDTO {

  private String measurement;
  private long offset;
  private long size;
  private double compressionRatio;

  // Extended information
  private String dataType;
  private String encoding;
  private String compression;
  private Long startTime;
  private Long endTime;
  private Long numOfPoints;
  private String minValue;
  private String maxValue;
  private String device;

  /** Default constructor for JSON deserialization. */
  public ChunkDTO() {}

  /**
   * Creates a new Chunk DTO with basic information.
   *
   * @param measurement measurement name this chunk belongs to
   * @param offset byte offset of the chunk in the file
   * @param size size of the chunk in bytes
   * @param compressionRatio compression ratio (uncompressed size / compressed size)
   */
  public ChunkDTO(String measurement, long offset, long size, double compressionRatio) {
    this.measurement = measurement;
    this.offset = offset;
    this.size = size;
    this.compressionRatio = compressionRatio;
  }

  /**
   * Creates a new Chunk DTO with extended information.
   *
   * @param measurement measurement name
   * @param offset byte offset
   * @param size chunk size in bytes
   * @param compressionRatio compression ratio
   * @param dataType data type
   * @param encoding encoding type
   * @param compression compression type
   * @param startTime start timestamp
   * @param endTime end timestamp
   * @param numOfPoints number of data points
   * @param minValue minimum value
   * @param maxValue maximum value
   * @param device device name
   */
  public ChunkDTO(
      String measurement,
      long offset,
      long size,
      double compressionRatio,
      String dataType,
      String encoding,
      String compression,
      Long startTime,
      Long endTime,
      Long numOfPoints,
      String minValue,
      String maxValue,
      String device) {
    this.measurement = measurement;
    this.offset = offset;
    this.size = size;
    this.compressionRatio = compressionRatio;
    this.dataType = dataType;
    this.encoding = encoding;
    this.compression = compression;
    this.startTime = startTime;
    this.endTime = endTime;
    this.numOfPoints = numOfPoints;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.device = device;
  }

  public String getMeasurement() {
    return measurement;
  }

  public void setMeasurement(String measurement) {
    this.measurement = measurement;
  }

  public long getOffset() {
    return offset;
  }

  public void setOffset(long offset) {
    this.offset = offset;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public double getCompressionRatio() {
    return compressionRatio;
  }

  public void setCompressionRatio(double compressionRatio) {
    this.compressionRatio = compressionRatio;
  }

  public String getDataType() {
    return dataType;
  }

  public void setDataType(String dataType) {
    this.dataType = dataType;
  }

  public String getEncoding() {
    return encoding;
  }

  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  public String getCompression() {
    return compression;
  }

  public void setCompression(String compression) {
    this.compression = compression;
  }

  public Long getStartTime() {
    return startTime;
  }

  public void setStartTime(Long startTime) {
    this.startTime = startTime;
  }

  public Long getEndTime() {
    return endTime;
  }

  public void setEndTime(Long endTime) {
    this.endTime = endTime;
  }

  public Long getNumOfPoints() {
    return numOfPoints;
  }

  public void setNumOfPoints(Long numOfPoints) {
    this.numOfPoints = numOfPoints;
  }

  public String getMinValue() {
    return minValue;
  }

  public void setMinValue(String minValue) {
    this.minValue = minValue;
  }

  public String getMaxValue() {
    return maxValue;
  }

  public void setMaxValue(String maxValue) {
    this.maxValue = maxValue;
  }

  public String getDevice() {
    return device;
  }

  public void setDevice(String device) {
    this.device = device;
  }

  /** Builder class for creating ChunkDTO instances. */
  public static class Builder {
    private String measurement;
    private long offset;
    private long size;
    private double compressionRatio;
    private String dataType;
    private String encoding;
    private String compression;
    private Long startTime;
    private Long endTime;
    private Long numOfPoints;
    private String minValue;
    private String maxValue;
    private String device;

    public Builder measurement(String measurement) {
      this.measurement = measurement;
      return this;
    }

    public Builder offset(long offset) {
      this.offset = offset;
      return this;
    }

    public Builder size(long size) {
      this.size = size;
      return this;
    }

    public Builder compressionRatio(double compressionRatio) {
      this.compressionRatio = compressionRatio;
      return this;
    }

    public Builder dataType(String dataType) {
      this.dataType = dataType;
      return this;
    }

    public Builder encoding(String encoding) {
      this.encoding = encoding;
      return this;
    }

    public Builder compression(String compression) {
      this.compression = compression;
      return this;
    }

    public Builder startTime(Long startTime) {
      this.startTime = startTime;
      return this;
    }

    public Builder endTime(Long endTime) {
      this.endTime = endTime;
      return this;
    }

    public Builder numOfPoints(Long numOfPoints) {
      this.numOfPoints = numOfPoints;
      return this;
    }

    public Builder minValue(String minValue) {
      this.minValue = minValue;
      return this;
    }

    public Builder maxValue(String maxValue) {
      this.maxValue = maxValue;
      return this;
    }

    public Builder device(String device) {
      this.device = device;
      return this;
    }

    public ChunkDTO build() {
      return new ChunkDTO(
          measurement,
          offset,
          size,
          compressionRatio,
          dataType,
          encoding,
          compression,
          startTime,
          endTime,
          numOfPoints,
          minValue,
          maxValue,
          device);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
