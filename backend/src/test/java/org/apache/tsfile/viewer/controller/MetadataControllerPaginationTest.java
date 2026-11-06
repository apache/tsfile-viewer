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

package org.apache.tsfile.viewer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.apache.tsfile.viewer.dto.ChunkDTO;
import org.apache.tsfile.viewer.dto.DeviceRefDTO;
import org.apache.tsfile.viewer.dto.MeasurementDTO;
import org.apache.tsfile.viewer.dto.PageDTO;
import org.apache.tsfile.viewer.dto.RowGroupDTO;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.service.MetadataService;

/**
 * Tests for the paginated metadata endpoints: parameter defaults, clamping and response shape.
 *
 * <p>Validates: offset default, offset clamping, limit cap, empty page past the end, JSON contract
 * and the 404 semantics of an unknown fileId.
 */
class MetadataControllerPaginationTest {

  private static final int TOTAL_ROW_GROUPS = 87_488;
  private static final int TOTAL_CHUNKS = 2_000_000;

  private MetadataService metadataService;
  private MetadataController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    metadataService = mock(MetadataService.class);
    controller = new MetadataController(metadataService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  @DisplayName("row-groups defaults to offset=0 and limit=50")
  void shouldApplyRowGroupDefaults() throws Exception {
    when(metadataService.getRowGroupsPage("file-1", 0, 50))
        .thenReturn(PageDTO.of(List.of(rowGroup(0, "device_0")), TOTAL_ROW_GROUPS, 0, 50));

    MvcResult result =
        mockMvc
            .perform(get("/api/meta/file-1/row-groups"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50))
            .andExpect(jsonPath("$.total").value(TOTAL_ROW_GROUPS))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].device").value("device_0"))
            .andExpect(jsonPath("$.items[0].index").value(0))
            .andReturn();

    System.out.println("[row-groups] " + result.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("negative offset becomes 0 and limit above 200 is capped")
  void shouldClampRowGroupParameters() throws Exception {
    when(metadataService.getRowGroupsPage("file-1", 0, 200))
        .thenReturn(PageDTO.of(List.of(rowGroup(0, "device_0")), TOTAL_ROW_GROUPS, 0, 200));

    MvcResult result =
        mockMvc
            .perform(get("/api/meta/file-1/row-groups").param("offset", "-7").param("limit", "100000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(200))
            .andReturn();

    verify(metadataService).getRowGroupsPage("file-1", 0, 200);
    System.out.println("[row-groups clamped] " + result.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("offset beyond total returns an empty page with the real total")
  void shouldReturnEmptyPagePastTheEnd() throws Exception {
    when(metadataService.getRowGroupsPage("file-1", 100_000, 50))
        .thenReturn(PageDTO.of(List.of(), TOTAL_ROW_GROUPS, 100_000, 50));

    MvcResult result =
        mockMvc
            .perform(get("/api/meta/file-1/row-groups").param("offset", "100000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.offset").value(100_000))
            .andExpect(jsonPath("$.total").value(TOTAL_ROW_GROUPS))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.items.length()").value(0))
            .andReturn();

    System.out.println("[row-groups past end] " + result.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("chunks defaults to offset=0 and limit=50 and returns the element contract")
  void shouldApplyChunkDefaults() throws Exception {
    when(metadataService.getChunksPage("file-1", 0, 50))
        .thenReturn(PageDTO.of(List.of(chunk("s0", 0, "device_0")), TOTAL_CHUNKS, 0, 50));

    MvcResult result =
        mockMvc
            .perform(get("/api/meta/file-1/chunks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50))
            .andExpect(jsonPath("$.total").value(TOTAL_CHUNKS))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.items[0].measurement").value("s0"))
            .andExpect(jsonPath("$.items[0].device").value("device_0"))
            .andExpect(jsonPath("$.items[0].numOfPoints").value(10))
            .andReturn();

    System.out.println("[chunks] " + result.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("chunks honours offset and limit inside the page")
  void shouldForwardChunkPageWindow() throws Exception {
    when(metadataService.getChunksPage("file-1", 100, 3))
        .thenReturn(PageDTO.of(List.of(chunk("s1", 40, "device_1")), TOTAL_CHUNKS, 100, 3));

    mockMvc
        .perform(get("/api/meta/file-1/chunks").param("offset", "100").param("limit", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offset").value(100))
        .andExpect(jsonPath("$.limit").value(3))
        .andExpect(jsonPath("$.hasMore").value(true));

    verify(metadataService).getChunksPage("file-1", 100, 3);
  }

  @Test
  @DisplayName("unknown fileId keeps the existing 404 exception semantics")
  void shouldPropagateNotFound() throws Exception {
    when(metadataService.getRowGroupsPage(eq("missing"), anyInt(), anyInt()))
        .thenThrow(new TsFileNotFoundException("File not found: missing", "missing"));
    when(metadataService.getChunksPage(eq("missing"), anyInt(), anyInt()))
        .thenThrow(new TsFileNotFoundException("File not found: missing", "missing"));

    assertThatThrownBy(() -> controller.getRowGroups("missing", null, null))
        .isInstanceOf(TsFileNotFoundException.class)
        .hasMessageContaining("missing");
    assertThatThrownBy(() -> controller.getChunks("missing", null, null))
        .isInstanceOf(TsFileNotFoundException.class)
        .hasMessageContaining("missing");
  }

  @Test
  @DisplayName("summary response keeps rowGroups and chunks as empty arrays")
  void shouldKeepSummaryContractWithEmptyDetailLists() throws Exception {
    org.apache.tsfile.viewer.dto.TsFileMetadataDTO summary =
        org.apache.tsfile.viewer.dto.TsFileMetadataDTO.builder()
            .fileId("file-1")
            .version("V3")
            .deviceCount(87_488)
            .measurementCount(12)
            .rowGroupCount(87_488)
            .chunkCount(2_000_000)
            .measurements(List.of())
            .rowGroups(List.of())
            .chunks(List.of())
            .tables(List.of())
            .build();
    when(metadataService.getMetadata("file-1")).thenReturn(summary);

    mockMvc
        .perform(get("/api/meta/file-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rowGroups").isArray())
        .andExpect(jsonPath("$.rowGroups.length()").value(0))
        .andExpect(jsonPath("$.chunks").isArray())
        .andExpect(jsonPath("$.chunks.length()").value(0))
        .andExpect(jsonPath("$.rowGroupCount").value(87_488))
        .andExpect(jsonPath("$.chunkCount").value(2_000_000));

    assertThat(summary.getRowGroups()).isEmpty();
    assertThat(summary.getChunks()).isEmpty();
  }

  private static RowGroupDTO rowGroup(int index, String device) {
    return RowGroupDTO.builder()
        .index(index)
        .device(device)
        .startTime(1_700_000_000_000L)
        .endTime(1_700_000_001_000L)
        .chunkCount(3)
        .build();
  }

  private static ChunkDTO chunk(String measurement, long offset, String device) {
    return ChunkDTO.builder()
        .measurement(measurement)
        .offset(offset)
        .size(128)
        .compressionRatio(2.5)
        .device(device)
        .dataType("INT64")
        .encoding("RLE")
        .compression("SNAPPY")
        .startTime(1_700_000_000_000L)
        .endTime(1_700_000_001_000L)
        .numOfPoints(10L)
        .minValue("1")
        .maxValue("10")
        .build();
  }
  @Test
  @DisplayName("device measurements are returned for a known device")
  void shouldReturnDeviceMeasurements() throws Exception {
    when(metadataService.getDeviceMeasurements("file-1", "root.a.b"))
        .thenReturn(
            List.of(
                MeasurementDTO.builder().name("temperature").dataType("FLOAT").build(),
                MeasurementDTO.builder().name("humidity").dataType("INT32").build()));

    mockMvc
        .perform(get("/api/meta/file-1/devices/root.a.b/measurements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.device").value("root.a.b"))
        .andExpect(jsonPath("$.measurements.length()").value(2))
        .andExpect(jsonPath("$.measurements[0].name").value("temperature"));
  }

  @Test
  @DisplayName("device measurements are empty for an unknown device")
  void shouldReturnEmptyMeasurementsForUnknownDevice() throws Exception {
    when(metadataService.getDeviceMeasurements("file-1", "root.unknown")).thenReturn(List.of());

    mockMvc
        .perform(get("/api/meta/file-1/devices/root.unknown/measurements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.device").value("root.unknown"))
        .andExpect(jsonPath("$.measurements.length()").value(0));
  }

  @Test
  @DisplayName("devices default to offset=0 and limit=50")
  void shouldApplyDeviceDefaults() throws Exception {
    when(metadataService.getDevicesPage("file-1", "t1", null, 0, 50))
        .thenReturn(
            PageDTO.of(
                List.of(new DeviceRefDTO("t1.d1", "t1", List.of("d1"))), 3, 0, 50));

    mockMvc
        .perform(get("/api/meta/file-1/devices").param("tableName", "t1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offset").value(0))
        .andExpect(jsonPath("$.limit").value(50))
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.hasMore").value(true))
        .andExpect(jsonPath("$.items[0].device").value("t1.d1"))
        .andExpect(jsonPath("$.items[0].tableName").value("t1"));
  }

  @Test
  @DisplayName("devices clamp limit to 200 and normalise a negative offset")
  void shouldClampDeviceParameters() throws Exception {
    when(metadataService.getDevicesPage("file-1", null, "abc", 0, 200))
        .thenReturn(PageDTO.of(List.of(), 0, 0, 200));

    mockMvc
        .perform(
            get("/api/meta/file-1/devices")
                .param("query", "abc")
                .param("offset", "-5")
                .param("limit", "500"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offset").value(0))
        .andExpect(jsonPath("$.limit").value(200));
  }
}
