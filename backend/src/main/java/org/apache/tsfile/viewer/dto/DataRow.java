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

import java.util.Map;

/**
 * DTO representing a single data row from a TsFile.
 *
 * <p>Validates: Requirement 3.1 (Data preview)
 */
public class DataRow {

  private long timestamp;
  private String device;
  private Map<String, Object> measurements;

  /** Default constructor for JSON deserialization. */
  public DataRow() {}

  /**
   * Creates a new data row.
   *
   * @param timestamp the timestamp of this row
   * @param device the device identifier
   * @param measurements map of measurement name to value
   */
  public DataRow(long timestamp, String device, Map<String, Object> measurements) {
    this.timestamp = timestamp;
    this.device = device;
    this.measurements = measurements;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public String getDevice() {
    return device;
  }

  public void setDevice(String device) {
    this.device = device;
  }

  public Map<String, Object> getMeasurements() {
    return measurements;
  }

  public void setMeasurements(Map<String, Object> measurements) {
    this.measurements = measurements;
  }

  /** Builder class for creating DataRow instances. */
  public static class Builder {
    private long timestamp;
    private String device;
    private Map<String, Object> measurements;

    public Builder timestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder device(String device) {
      this.device = device;
      return this;
    }

    public Builder measurements(Map<String, Object> measurements) {
      this.measurements = measurements;
      return this;
    }

    public DataRow build() {
      return new DataRow(timestamp, device, measurements);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
