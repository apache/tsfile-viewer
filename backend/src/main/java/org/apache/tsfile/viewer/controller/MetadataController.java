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

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.apache.tsfile.viewer.dto.ChunkDTO;
import org.apache.tsfile.viewer.dto.DeviceMeasurementsDTO;
import org.apache.tsfile.viewer.dto.DeviceRefDTO;
import org.apache.tsfile.viewer.dto.MeasurementDTO;
import org.apache.tsfile.viewer.dto.PageDTO;
import org.apache.tsfile.viewer.dto.RowGroupDTO;
import org.apache.tsfile.viewer.dto.TsFileMetadataDTO;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.service.MetadataService;

/**
 * REST controller for TsFile metadata operations.
 *
 * <p>Provides endpoints for:
 *
 * <ul>
 *   <li>GET /api/meta/{fileId} - Get the metadata summary
 *   <li>GET /api/meta/{fileId}/row-groups - Get a page of RowGroups
 *   <li>GET /api/meta/{fileId}/chunks - Get a page of Chunks
 * </ul>
 *
 * <p>Validates: Requirement 8.3 (Metadata endpoint)
 */
@RestController
@RequestMapping("/api/meta")
public class MetadataController {

  private static final Logger logger = LoggerFactory.getLogger(MetadataController.class);

  private final MetadataService metadataService;

  public MetadataController(MetadataService metadataService) {
    this.metadataService = metadataService;
  }

  /**
   * Gets the metadata summary for a TsFile.
   *
   * <p>Returns version, time range, device/measurement counts and schema details. The {@code
   * rowGroups} and {@code chunks} fields are kept for contract compatibility but are always empty;
   * details come from the paging endpoints below.
   *
   * @param fileId the file identifier
   * @return TsFileMetadataDTO with summary metadata
   */
  @GetMapping("/{fileId}")
  public ResponseEntity<TsFileMetadataDTO> getMetadata(@PathVariable String fileId)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting metadata for fileId={}", fileId);

    TsFileMetadataDTO metadata = metadataService.getMetadata(fileId);
    return ResponseEntity.ok(metadata);
  }

  /**
   * Gets one page of devices, optionally scoped to a table and filtered by a substring.
   *
   * <p>Cheap enough for pickers: it is derived from the cached device index, not from a table
   * query.
   *
   * @param fileId the file identifier
   * @param tableName only devices of this table
   * @param query case-insensitive substring the device name must contain
   * @param offset zero-based offset, negative values are treated as 0
   * @param limit page size, defaults to 50 and is capped at 200
   * @return page of devices
   */
  @GetMapping("/{fileId}/devices")
  public ResponseEntity<PageDTO<DeviceRefDTO>> getDevices(
      @PathVariable String fileId,
      @RequestParam(required = false) String tableName,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer limit)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    int effectiveOffset = PageDTO.normalizeOffset(offset);
    int effectiveLimit = PageDTO.normalizeLimit(limit);

    PageDTO<DeviceRefDTO> page =
        metadataService.getDevicesPage(fileId, tableName, query, effectiveOffset, effectiveLimit);
    return ResponseEntity.ok(page);
  }

  /**
   * Gets the measurements carried by one device.
   *
   * <p>Tree model files nest measurements under devices, so a client showing a device picker needs
   * the measurement choices of the selected device instead of the whole file's union.
   *
   * @param fileId the file identifier
   * @param device device name as returned by the RowGroup paging endpoint
   * @return measurements of that device, empty when the device is not part of the file
   */
  @GetMapping("/{fileId}/devices/{device}/measurements")
  public ResponseEntity<DeviceMeasurementsDTO> getDeviceMeasurements(
      @PathVariable String fileId, @PathVariable String device)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting measurements of device={} for fileId={}", device, fileId);

    List<MeasurementDTO> measurements = metadataService.getDeviceMeasurements(fileId, device);
    return ResponseEntity.ok(new DeviceMeasurementsDTO(device, measurements));
  }

  /**
   * Gets one page of RowGroups of a TsFile.
   *
   * @param fileId the file identifier
   * @param offset zero-based offset, negative values are treated as 0
   * @param limit page size, defaults to 50 and is capped at 200
   * @return page of RowGroups
   */
  @GetMapping("/{fileId}/row-groups")
  public ResponseEntity<PageDTO<RowGroupDTO>> getRowGroups(
      @PathVariable String fileId,
      @RequestParam(name = "offset", required = false) Integer offset,
      @RequestParam(name = "limit", required = false) Integer limit)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    int pageOffset = PageDTO.normalizeOffset(offset);
    int pageLimit = PageDTO.normalizeLimit(limit);

    logger.debug(
        "Getting RowGroup page for fileId={}, offset={}, limit={}", fileId, pageOffset, pageLimit);

    PageDTO<RowGroupDTO> page = metadataService.getRowGroupsPage(fileId, pageOffset, pageLimit);
    return ResponseEntity.ok(page);
  }

  /**
   * Gets one page of Chunks of a TsFile.
   *
   * @param fileId the file identifier
   * @param offset zero-based offset, negative values are treated as 0
   * @param limit page size, defaults to 50 and is capped at 200
   * @return page of Chunks
   */
  @GetMapping("/{fileId}/chunks")
  public ResponseEntity<PageDTO<ChunkDTO>> getChunks(
      @PathVariable String fileId,
      @RequestParam(name = "offset", required = false) Integer offset,
      @RequestParam(name = "limit", required = false) Integer limit)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    int pageOffset = PageDTO.normalizeOffset(offset);
    int pageLimit = PageDTO.normalizeLimit(limit);

    logger.debug(
        "Getting chunk page for fileId={}, offset={}, limit={}", fileId, pageOffset, pageLimit);

    PageDTO<ChunkDTO> page = metadataService.getChunksPage(fileId, pageOffset, pageLimit);
    return ResponseEntity.ok(page);
  }
}
