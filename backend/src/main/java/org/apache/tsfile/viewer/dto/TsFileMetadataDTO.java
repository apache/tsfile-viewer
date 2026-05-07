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

import java.util.List;

/**
 * DTO representing complete TsFile metadata.
 *
 * <p>Contains all metadata information about a TsFile including version, time range, counts, and
 * detailed lists of measurements, RowGroups, and Chunks.
 *
 * <p>Validates: Requirement 2 (Metadata display)
 */
public class TsFileMetadataDTO {

  private String fileId;
  private String version;
  private TimeRange timeRange;
  private int deviceCount;
  private int measurementCount;
  private int rowGroupCount;
  private int chunkCount;
  private List<MeasurementDTO> measurements;
  private List<RowGroupDTO> rowGroups;
  private List<ChunkDTO> chunks;
  private List<TableDTO> tables;

  /** Default constructor for JSON deserialization. */
  public TsFileMetadataDTO() {}

  public String getFileId() {
    return fileId;
  }

  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public TimeRange getTimeRange() {
    return timeRange;
  }

  public void setTimeRange(TimeRange timeRange) {
    this.timeRange = timeRange;
  }

  public int getDeviceCount() {
    return deviceCount;
  }

  public void setDeviceCount(int deviceCount) {
    this.deviceCount = deviceCount;
  }

  public int getMeasurementCount() {
    return measurementCount;
  }

  public void setMeasurementCount(int measurementCount) {
    this.measurementCount = measurementCount;
  }

  public int getRowGroupCount() {
    return rowGroupCount;
  }

  public void setRowGroupCount(int rowGroupCount) {
    this.rowGroupCount = rowGroupCount;
  }

  public int getChunkCount() {
    return chunkCount;
  }

  public void setChunkCount(int chunkCount) {
    this.chunkCount = chunkCount;
  }

  public List<MeasurementDTO> getMeasurements() {
    return measurements;
  }

  public void setMeasurements(List<MeasurementDTO> measurements) {
    this.measurements = measurements;
  }

  public List<RowGroupDTO> getRowGroups() {
    return rowGroups;
  }

  public void setRowGroups(List<RowGroupDTO> rowGroups) {
    this.rowGroups = rowGroups;
  }

  public List<ChunkDTO> getChunks() {
    return chunks;
  }

  public void setChunks(List<ChunkDTO> chunks) {
    this.chunks = chunks;
  }

  public List<TableDTO> getTables() {
    return tables;
  }

  public void setTables(List<TableDTO> tables) {
    this.tables = tables;
  }

  /** Builder class for creating TsFileMetadataDTO instances. */
  public static class Builder {
    private String fileId;
    private String version;
    private TimeRange timeRange;
    private int deviceCount;
    private int measurementCount;
    private int rowGroupCount;
    private int chunkCount;
    private List<MeasurementDTO> measurements;
    private List<RowGroupDTO> rowGroups;
    private List<ChunkDTO> chunks;
    private List<TableDTO> tables;

    public Builder fileId(String fileId) {
      this.fileId = fileId;
      return this;
    }

    public Builder version(String version) {
      this.version = version;
      return this;
    }

    public Builder timeRange(TimeRange timeRange) {
      this.timeRange = timeRange;
      return this;
    }

    public Builder deviceCount(int deviceCount) {
      this.deviceCount = deviceCount;
      return this;
    }

    public Builder measurementCount(int measurementCount) {
      this.measurementCount = measurementCount;
      return this;
    }

    public Builder rowGroupCount(int rowGroupCount) {
      this.rowGroupCount = rowGroupCount;
      return this;
    }

    public Builder chunkCount(int chunkCount) {
      this.chunkCount = chunkCount;
      return this;
    }

    public Builder measurements(List<MeasurementDTO> measurements) {
      this.measurements = measurements;
      return this;
    }

    public Builder rowGroups(List<RowGroupDTO> rowGroups) {
      this.rowGroups = rowGroups;
      return this;
    }

    public Builder chunks(List<ChunkDTO> chunks) {
      this.chunks = chunks;
      return this;
    }

    public Builder tables(List<TableDTO> tables) {
      this.tables = tables;
      return this;
    }

    public TsFileMetadataDTO build() {
      TsFileMetadataDTO dto = new TsFileMetadataDTO();
      dto.setFileId(fileId);
      dto.setVersion(version);
      dto.setTimeRange(timeRange);
      dto.setDeviceCount(deviceCount);
      dto.setMeasurementCount(measurementCount);
      dto.setRowGroupCount(rowGroupCount);
      dto.setChunkCount(chunkCount);
      dto.setMeasurements(measurements);
      dto.setRowGroups(rowGroups);
      dto.setChunks(chunks);
      dto.setTables(tables);
      return dto;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
