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
 * A device reference for pickers: its identifier, owning table and TAG values.
 *
 * <p>Derived from the device identifier itself, which is {@code tableName.tag1.tag2...}, so it
 * needs no row scan.
 */
public class DeviceRefDTO {

  private String device;
  private String tableName;
  private List<String> tagValues;

  public DeviceRefDTO() {}

  public DeviceRefDTO(String device, String tableName, List<String> tagValues) {
    this.device = device;
    this.tableName = tableName;
    this.tagValues = tagValues;
  }

  public String getDevice() {
    return device;
  }

  public void setDevice(String device) {
    this.device = device;
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
}
