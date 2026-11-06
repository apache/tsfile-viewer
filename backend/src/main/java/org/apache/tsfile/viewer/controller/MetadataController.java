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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.apache.tsfile.viewer.dto.ChunkDTO;
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
