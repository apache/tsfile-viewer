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
 * Measurements carried by a single device.
 *
 * <p>Tree model files nest measurements under devices, so the measurement options a client offers
 * must be scoped to the selected device rather than to the whole file.
 */
public class DeviceMeasurementsDTO {

  private String device;
  private List<MeasurementDTO> measurements;

  public DeviceMeasurementsDTO() {}

  public DeviceMeasurementsDTO(String device, List<MeasurementDTO> measurements) {
    this.device = device;
    this.measurements = measurements;
  }

  public String getDevice() {
    return device;
  }

  public void setDevice(String device) {
    this.device = device;
  }

  public List<MeasurementDTO> getMeasurements() {
    return measurements;
  }

  public void setMeasurements(List<MeasurementDTO> measurements) {
    this.measurements = measurements;
  }
}
