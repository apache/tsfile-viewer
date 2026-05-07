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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.dto.TreeNode;
import org.apache.tsfile.viewer.dto.UploadResponse;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.InvalidFileException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.service.FileService;

/**
 * REST controller for file management operations.
 *
 * <p>Provides endpoints for:
 *
 * <ul>
 *   <li>GET /api/files/tree - Browse directory tree with lazy loading
 *   <li>POST /api/files/upload - Upload TsFile files
 * </ul>
 *
 * <p>Validates: Requirement 8.1, 8.2 (File API endpoints)
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

  private static final Logger logger = LoggerFactory.getLogger(FileController.class);

  private final FileService fileService;
  private final TsFileProperties tsFileProperties;

  public FileController(FileService fileService, TsFileProperties tsFileProperties) {
    this.fileService = fileService;
    this.tsFileProperties = tsFileProperties;
  }

  /**
   * Gets the file tree for a given root directory.
   *
   * <p>Supports lazy loading by providing an optional path parameter to load subdirectory contents.
   * If root parameter is not provided or empty, defaults to the first allowed directory.
   *
   * @param root the root directory path (optional, defaults to first allowed directory)
   * @param path optional subdirectory path for lazy loading
   * @return TreeNode representing the directory structure
   */
  @GetMapping("/tree")
  public ResponseEntity<TreeNode> getFileTree(
      @RequestParam(required = false) String root, @RequestParam(required = false) String path)
      throws AccessDeniedException, TsFileNotFoundException, IOException {
    // 如果 root 为空，使用第一个允许的目录
    if (root == null || root.trim().isEmpty()) {
      if (tsFileProperties.getAllowedDirectories().isEmpty()) {
        throw new AccessDeniedException(
            "No allowed directories configured. Please configure tsfile.allowed-directories in application.yml");
      }
      root = tsFileProperties.getAllowedDirectories().get(0);
      logger.debug("Root parameter not provided, using default: {}", root);
    }

    logger.debug("Getting file tree: root={}, path={}", root, path);

    TreeNode tree = fileService.getFileTree(root, path);
    return ResponseEntity.ok(tree);
  }

  /**
   * Uploads a TsFile.
   *
   * <p>Validates the file extension (.tsfile) and stores the file in the configured upload
   * directory. Returns a unique fileId that can be used to access the file.
   *
   * @param file the TsFile to upload
   * @return UploadResponse with fileId and metadata
   */
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file)
      throws InvalidFileException, AccessDeniedException, IOException {
    logger.info("Uploading file: name={}, size={}", file.getOriginalFilename(), file.getSize());

    UploadResponse response = fileService.uploadFile(file);
    logger.info("File uploaded successfully: fileId={}", response.getFileId());

    return ResponseEntity.ok(response);
  }
}
