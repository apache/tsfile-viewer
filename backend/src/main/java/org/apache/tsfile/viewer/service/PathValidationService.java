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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.tsfile.viewer.config.TsFileProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for validating file paths against the configured directory whitelist.
 *
 * <p>This service ensures that all file access is restricted to directories explicitly allowed in
 * the configuration. It prevents path traversal attacks and unauthorized file system access.
 *
 * <p>Validates: Requirement 7.6 (Directory whitelist security)
 */
@Service
public class PathValidationService {

  private static final Logger logger = LoggerFactory.getLogger(PathValidationService.class);

  private final TsFileProperties tsFileProperties;

  public PathValidationService(TsFileProperties tsFileProperties) {
    this.tsFileProperties = tsFileProperties;
  }

  /**
   * Validates that the given path is within the allowed directories whitelist.
   *
   * <p>This method performs the following security checks:
   *
   * <ul>
   *   <li>Rejects null or empty paths
   *   <li>Rejects paths containing ".." (parent directory traversal)
   *   <li>Normalizes the path to resolve any symbolic links or relative components
   *   <li>Verifies the normalized path starts with one of the allowed directories
   * </ul>
   *
   * @param pathString the path to validate
   * @return true if the path is within an allowed directory, false otherwise
   */
  public boolean isPathAllowed(String pathString) {
    if (pathString == null || pathString.isBlank()) {
      logger.warn("Path validation failed: path is null or empty");
      return false;
    }

    // Reject paths with explicit parent directory traversal
    if (containsPathTraversal(pathString)) {
      logger.warn("Path validation failed: path contains traversal sequence: {}", pathString);
      return false;
    }

    try {
      Path path = Paths.get(pathString).toAbsolutePath().normalize();
      List<String> allowedDirectories = tsFileProperties.getAllowedDirectories();

      if (allowedDirectories == null || allowedDirectories.isEmpty()) {
        logger.warn("Path validation failed: no allowed directories configured");
        return false;
      }

      for (String allowedDir : allowedDirectories) {
        Path allowedPath = Paths.get(allowedDir).toAbsolutePath().normalize();
        if (path.startsWith(allowedPath)) {
          logger.debug("Path {} is within allowed directory {}", pathString, allowedDir);
          return true;
        }
      }

      logger.warn("Path validation failed: {} is not within any allowed directory", pathString);
      return false;

    } catch (InvalidPathException e) {
      logger.warn("Path validation failed: invalid path syntax: {}", pathString, e);
      return false;
    }
  }

  /**
   * Validates the path and throws an exception if it's not allowed.
   *
   * @param pathString the path to validate
   * @throws PathNotAllowedException if the path is not within an allowed directory
   */
  public void validatePath(String pathString) throws PathNotAllowedException {
    if (!isPathAllowed(pathString)) {
      throw new PathNotAllowedException(
          "Access denied: path is outside allowed directories: " + pathString);
    }
  }

  /**
   * Checks if the path contains path traversal sequences.
   *
   * <p>Detects common path traversal patterns including:
   *
   * <ul>
   *   <li>".." - parent directory reference
   *   <li>Encoded variants like "%2e%2e"
   * </ul>
   *
   * @param path the path string to check
   * @return true if the path contains traversal sequences
   */
  private boolean containsPathTraversal(String path) {
    // Check for ".." in various forms
    if (path.contains("..")) {
      return true;
    }

    // Check for URL-encoded ".." (%2e%2e or %2E%2E)
    String lowerPath = path.toLowerCase();
    if (lowerPath.contains("%2e%2e") || lowerPath.contains("%2e.") || lowerPath.contains(".%2e")) {
      return true;
    }

    return false;
  }

  /**
   * Gets the list of allowed directories from configuration.
   *
   * @return list of allowed directory paths
   */
  public List<String> getAllowedDirectories() {
    return tsFileProperties.getAllowedDirectories();
  }

  /**
   * Checks if the upload directory is configured and valid.
   *
   * @return true if upload directory is configured
   */
  public boolean isUploadDirectoryConfigured() {
    String uploadDir = tsFileProperties.getUploadDirectory();
    return uploadDir != null && !uploadDir.isBlank();
  }

  /**
   * Gets the configured upload directory.
   *
   * @return the upload directory path, or null if not configured
   */
  public String getUploadDirectory() {
    return tsFileProperties.getUploadDirectory();
  }

  /**
   * Validates that the upload directory is within the allowed directories.
   *
   * @return true if the upload directory is allowed
   */
  public boolean isUploadDirectoryAllowed() {
    String uploadDir = tsFileProperties.getUploadDirectory();
    if (uploadDir == null || uploadDir.isBlank()) {
      return false;
    }
    return isPathAllowed(uploadDir);
  }

  /** Exception thrown when a path is not within the allowed directories. */
  public static class PathNotAllowedException extends Exception {

    public PathNotAllowedException(String message) {
      super(message);
    }
  }
}
