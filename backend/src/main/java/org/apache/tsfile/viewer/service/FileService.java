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

package org.apache.tsfile.viewer.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.dto.FileInfo;
import org.apache.tsfile.viewer.dto.TreeNode;
import org.apache.tsfile.viewer.dto.UploadResponse;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.InvalidFileException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;

/**
 * Service for file management operations.
 *
 * <p>Provides functionality for:
 *
 * <ul>
 *   <li>Browsing directory tree with lazy loading
 *   <li>Uploading TsFile files with validation
 *   <li>Path validation against whitelist
 *   <li>File ID management and mapping
 * </ul>
 *
 * <p>Validates: Requirement 1.1-1.5 (File tree, upload)
 */
@Service
public class FileService {

  private static final Logger logger = LoggerFactory.getLogger(FileService.class);
  private static final String TSFILE_EXTENSION = ".tsfile";

  private final TsFileProperties tsFileProperties;
  private final PathValidationService pathValidationService;

  /**
   * Maps fileId to actual file path for uploaded and accessed files. LRU-bounded to prevent memory
   * leak.
   */
  private final Cache<String, String> fileIdToPath =
      Caffeine.newBuilder().maximumSize(10_000).expireAfterAccess(60, TimeUnit.MINUTES).build();

  public FileService(
      TsFileProperties tsFileProperties, PathValidationService pathValidationService) {
    this.tsFileProperties = tsFileProperties;
    this.pathValidationService = pathValidationService;
  }

  /**
   * Gets the file tree for a given root directory with optional path for lazy loading.
   *
   * <p>If path is null or empty, returns the root directory contents. Otherwise, returns the
   * contents of the specified subdirectory.
   *
   * @param root the root directory path
   * @param path optional subdirectory path for lazy loading
   * @return TreeNode representing the directory structure
   * @throws AccessDeniedException if the path is outside allowed directories
   * @throws TsFileNotFoundException if the path does not exist
   */
  public TreeNode getFileTree(String root, String path) throws AccessDeniedException, IOException {
    String targetPath = (path != null && !path.isBlank()) ? path : root;

    // Validate path against whitelist
    if (!pathValidationService.isPathAllowed(targetPath)) {
      logger.warn("Access denied for path: {}", targetPath);
      throw new AccessDeniedException("Access denied: path is outside allowed directories");
    }

    Path dirPath = Paths.get(targetPath).toAbsolutePath().normalize();

    if (!Files.exists(dirPath)) {
      throw new TsFileNotFoundException("Directory not found: " + targetPath);
    }

    if (!Files.isDirectory(dirPath)) {
      throw new InvalidFileException("Path is not a directory: " + targetPath);
    }

    return buildTreeNode(dirPath, true);
  }

  /**
   * Builds a TreeNode for the given path.
   *
   * @param path the file system path
   * @param loadChildren whether to load children for directories
   * @return TreeNode representing the path
   */
  private TreeNode buildTreeNode(Path path, boolean loadChildren) throws IOException {
    String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
    boolean isDirectory = Files.isDirectory(path);

    TreeNode node =
        TreeNode.builder()
            .name(name)
            .path(path.toString())
            .directory(isDirectory)
            .loaded(loadChildren || !isDirectory)
            .build();

    if (isDirectory && loadChildren) {
      List<TreeNode> children = new ArrayList<>();
      try (Stream<Path> stream = Files.list(path)) {
        stream
            .sorted(
                (p1, p2) -> {
                  // Directories first, then files
                  boolean d1 = Files.isDirectory(p1);
                  boolean d2 = Files.isDirectory(p2);
                  if (d1 != d2) {
                    return d1 ? -1 : 1;
                  }
                  return p1.getFileName()
                      .toString()
                      .compareToIgnoreCase(p2.getFileName().toString());
                })
            .filter(this::isVisibleFile)
            .forEach(
                childPath -> {
                  try {
                    // Don't load grandchildren (lazy loading)
                    children.add(buildTreeNode(childPath, false));
                  } catch (IOException e) {
                    logger.warn("Failed to read child path: {}", childPath, e);
                  }
                });
      }
      node.setChildren(children);
    }

    return node;
  }

  /**
   * Checks if a file should be visible in the tree.
   *
   * <p>Filters out hidden files and shows only directories and .tsfile files.
   *
   * @param path the path to check
   * @return true if the file should be visible
   */
  private boolean isVisibleFile(Path path) {
    try {
      if (Files.isHidden(path)) {
        return false;
      }
      String fileName = path.getFileName().toString();
      // Show directories and .tsfile files
      return Files.isDirectory(path) || fileName.toLowerCase().endsWith(TSFILE_EXTENSION);
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Uploads a TsFile and returns upload response with generated fileId.
   *
   * <p>Validates:
   *
   * <ul>
   *   <li>File extension must be .tsfile
   *   <li>File must not be empty
   *   <li>Upload directory must be configured and allowed
   * </ul>
   *
   * @param file the multipart file to upload
   * @return UploadResponse with fileId and metadata
   * @throws InvalidFileException if the file is invalid
   * @throws AccessDeniedException if upload directory is not allowed
   * @throws IOException if file cannot be saved
   */
  public UploadResponse uploadFile(MultipartFile file)
      throws InvalidFileException, AccessDeniedException, IOException {
    // Validate file
    validateUploadFile(file);

    // Get upload directory
    String uploadDir = tsFileProperties.getUploadDirectory();
    if (uploadDir == null || uploadDir.isBlank()) {
      throw new InvalidFileException("Upload directory is not configured");
    }

    if (!pathValidationService.isPathAllowed(uploadDir)) {
      throw new AccessDeniedException("Upload directory is not within allowed directories");
    }

    // Create upload directory if it doesn't exist
    Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
    if (!Files.exists(uploadPath)) {
      Files.createDirectories(uploadPath);
    }

    // Generate unique fileId
    String fileId = generateFileId();
    String originalFilename = file.getOriginalFilename();
    String safeFilename = sanitizeFilename(originalFilename);

    // Save file with fileId prefix to ensure uniqueness
    String storedFilename = fileId + "_" + safeFilename;
    Path targetPath = uploadPath.resolve(storedFilename);

    logger.info("Uploading file: {} -> {}", originalFilename, targetPath);
    file.transferTo(targetPath);

    // Register fileId mapping
    fileIdToPath.put(fileId, targetPath.toString());

    return UploadResponse.builder()
        .fileId(fileId)
        .fileName(originalFilename)
        .fileSize(file.getSize())
        .uploadTime(LocalDateTime.now())
        .build();
  }

  /**
   * Validates the uploaded file.
   *
   * @param file the file to validate
   * @throws InvalidFileException if validation fails
   */
  private void validateUploadFile(MultipartFile file) throws InvalidFileException {
    if (file == null || file.isEmpty()) {
      throw new InvalidFileException("File is empty or not provided");
    }

    String filename = file.getOriginalFilename();
    if (filename == null || filename.isBlank()) {
      throw new InvalidFileException("File name is required");
    }

    if (!filename.toLowerCase().endsWith(TSFILE_EXTENSION)) {
      throw new InvalidFileException("Invalid file extension. Only .tsfile files are allowed");
    }
  }

  /**
   * Sanitizes a filename to prevent path traversal and other security issues.
   *
   * @param filename the original filename
   * @return sanitized filename
   */
  private String sanitizeFilename(String filename) {
    if (filename == null) {
      return "unknown.tsfile";
    }
    // Remove path separators and other dangerous characters
    return filename
        .replaceAll("[/\\\\]", "_")
        .replaceAll("\\.\\.", "_")
        .replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  /**
   * Generates a unique file ID.
   *
   * @return unique file ID
   */
  private String generateFileId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  /**
   * Gets the file path for a given fileId.
   *
   * <p>First checks the fileId mapping, then decodes the fileId as base64-encoded path.
   *
   * @param fileId the file identifier (base64-encoded path or UUID from upload)
   * @return the file path
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   * @throws InvalidFileException if the fileId is invalid
   */
  public String getFilePath(String fileId) throws TsFileNotFoundException, AccessDeniedException {
    // Check if fileId is in our mapping (uploaded files)
    String path = fileIdToPath.getIfPresent(fileId);

    if (path != null) {
      if (!Files.exists(Paths.get(path))) {
        fileIdToPath.invalidate(fileId);
        throw new TsFileNotFoundException("File not found: " + fileId);
      }
      return path;
    }

    // Try to decode fileId as base64-encoded path (server-side files)
    String decodedPath;
    try {
      byte[] decodedBytes = Base64.getDecoder().decode(fileId);
      decodedPath = new String(decodedBytes, StandardCharsets.UTF_8);
      logger.debug("Decoded fileId from base64: {} -> {}", fileId, decodedPath);
    } catch (IllegalArgumentException e) {
      // If decoding fails, treat fileId as a direct path (fallback)
      logger.debug("Failed to decode fileId as base64, treating as direct path: {}", fileId);
      decodedPath = fileId;
    }

    // Validate the decoded path
    if (!pathValidationService.isPathAllowed(decodedPath)) {
      throw new AccessDeniedException("Access denied: path is outside allowed directories");
    }

    Path filePath = Paths.get(decodedPath).toAbsolutePath().normalize();
    if (!Files.exists(filePath)) {
      throw new TsFileNotFoundException("File not found: " + decodedPath);
    }

    if (!filePath.toString().toLowerCase().endsWith(TSFILE_EXTENSION)) {
      throw new InvalidFileException("Not a TsFile: " + decodedPath);
    }

    return filePath.toString();
  }

  /**
   * Registers a file path with a generated fileId.
   *
   * <p>Used when accessing server-side files to generate a consistent fileId.
   *
   * @param path the file path
   * @return the generated fileId
   * @throws AccessDeniedException if the path is not allowed
   * @throws TsFileNotFoundException if the file does not exist
   */
  public String registerFile(String path) throws AccessDeniedException, TsFileNotFoundException {
    if (!pathValidationService.isPathAllowed(path)) {
      throw new AccessDeniedException("Access denied: path is outside allowed directories");
    }

    Path filePath = Paths.get(path).toAbsolutePath().normalize();
    if (!Files.exists(filePath)) {
      throw new TsFileNotFoundException("File not found: " + path);
    }

    // Check if already registered
    for (Map.Entry<String, String> entry : fileIdToPath.asMap().entrySet()) {
      if (entry.getValue().equals(filePath.toString())) {
        return entry.getKey();
      }
    }

    // Generate new fileId
    String fileId = generateFileId();
    fileIdToPath.put(fileId, filePath.toString());

    return fileId;
  }

  /**
   * Gets file information for a given fileId.
   *
   * @param fileId the file identifier
   * @return FileInfo with file details
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   */
  public FileInfo getFileInfo(String fileId) throws TsFileNotFoundException, AccessDeniedException {
    String path = getFilePath(fileId);
    Path filePath = Paths.get(path);

    try {
      return FileInfo.builder()
          .fileId(fileId)
          .name(filePath.getFileName().toString())
          .path(path)
          .size(Files.size(filePath))
          .uploadTime(LocalDateTime.now()) // Could use file creation time
          .directory(false)
          .build();
    } catch (IOException e) {
      throw new TsFileNotFoundException("Cannot read file info: " + fileId);
    }
  }

  /**
   * Validates that a path is within the allowed directories.
   *
   * @param path the path to validate
   * @throws AccessDeniedException if the path is not allowed
   */
  public void validatePath(String path) throws AccessDeniedException {
    if (!pathValidationService.isPathAllowed(path)) {
      throw new AccessDeniedException("Access denied: path is outside allowed directories");
    }
  }

  /**
   * Checks if a file extension is valid (.tsfile).
   *
   * @param filename the filename to check
   * @return true if the extension is valid
   */
  public boolean isValidExtension(String filename) {
    return filename != null && filename.toLowerCase().endsWith(TSFILE_EXTENSION);
  }
}
