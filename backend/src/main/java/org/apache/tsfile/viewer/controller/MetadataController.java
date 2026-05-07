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
import org.springframework.web.bind.annotation.RestController;

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
 *   <li>GET /api/meta/{fileId} - Get complete TsFile metadata
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
   * Gets complete metadata for a TsFile.
   *
   * <p>Returns metadata including version, time range, device/measurement counts, and detailed
   * lists of measurements, RowGroups, and Chunks.
   *
   * @param fileId the file identifier
   * @return TsFileMetadataDTO with complete metadata
   */
  @GetMapping("/{fileId}")
  public ResponseEntity<TsFileMetadataDTO> getMetadata(@PathVariable String fileId)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    logger.debug("Getting metadata for fileId={}", fileId);

    TsFileMetadataDTO metadata = metadataService.getMetadata(fileId);
    return ResponseEntity.ok(metadata);
  }
}
