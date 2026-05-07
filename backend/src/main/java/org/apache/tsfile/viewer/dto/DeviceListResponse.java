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
 * DTO for device list response.
 *
 * <p>Returns a list of unique device identifiers available in a TsFile.
 */
public class DeviceListResponse {

  private List<DeviceInfo> devices;
  private int totalCount;

  /** Default constructor for JSON deserialization. */
  public DeviceListResponse() {}

  /**
   * Creates a new device list response.
   *
   * @param devices list of device information
   * @param totalCount total number of devices
   */
  public DeviceListResponse(List<DeviceInfo> devices, int totalCount) {
    this.devices = devices;
    this.totalCount = totalCount;
  }

  public List<DeviceInfo> getDevices() {
    return devices;
  }

  public void setDevices(List<DeviceInfo> devices) {
    this.devices = devices;
  }

  public int getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(int totalCount) {
    this.totalCount = totalCount;
  }

  /** Information about a single device. */
  public static class DeviceInfo {
    private String deviceId;
    private String tableName;
    private List<String> tagValues;
    private long dataPointCount;

    public DeviceInfo() {}

    public DeviceInfo(
        String deviceId, String tableName, List<String> tagValues, long dataPointCount) {
      this.deviceId = deviceId;
      this.tableName = tableName;
      this.tagValues = tagValues;
      this.dataPointCount = dataPointCount;
    }

    public String getDeviceId() {
      return deviceId;
    }

    public void setDeviceId(String deviceId) {
      this.deviceId = deviceId;
    }

    public String getTableName() {
      return tableName;
    }

    public void setTableName(String tableName) {
      this.tableName = tableName;
    }

    public List<String> getTagValues() {
      return tagValues;
    }

    public void setTagValues(List<String> tagValues) {
      this.tagValues = tagValues;
    }

    public long getDataPointCount() {
      return dataPointCount;
    }

    public void setDataPointCount(long dataPointCount) {
      this.dataPointCount = dataPointCount;
    }

    public static Builder builder() {
      return new Builder();
    }

    /** Builder for DeviceInfo. */
    public static class Builder {
      private String deviceId;
      private String tableName;
      private List<String> tagValues;
      private long dataPointCount;

      public Builder deviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
      }

      public Builder tableName(String tableName) {
        this.tableName = tableName;
        return this;
      }

      public Builder tagValues(List<String> tagValues) {
        this.tagValues = tagValues;
        return this;
      }

      public Builder dataPointCount(long dataPointCount) {
        this.dataPointCount = dataPointCount;
        return this;
      }

      public DeviceInfo build() {
        return new DeviceInfo(deviceId, tableName, tagValues, dataPointCount);
      }
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for DeviceListResponse. */
  public static class Builder {
    private List<DeviceInfo> devices;
    private int totalCount;

    public Builder devices(List<DeviceInfo> devices) {
      this.devices = devices;
      return this;
    }

    public Builder totalCount(int totalCount) {
      this.totalCount = totalCount;
      return this;
    }

    public DeviceListResponse build() {
      return new DeviceListResponse(devices, totalCount);
    }
  }
}
