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

import java.time.LocalDateTime;

/**
 * DTO representing file information.
 *
 * <p>Used for displaying file details in the recent files list and file selection UI. Contains
 * metadata about both uploaded and server-side TsFile files.
 *
 * <p>Validates: Requirement 1.7 (Recent files list)
 */
public class FileInfo {

  private String fileId;
  private String name;
  private String path;
  private long size;
  private LocalDateTime uploadTime;
  private boolean isDirectory;

  /** Default constructor for JSON deserialization. */
  public FileInfo() {}

  /**
   * Creates a new file info instance.
   *
   * @param fileId unique identifier for the file
   * @param name file name
   * @param path full path to the file
   * @param size file size in bytes
   * @param uploadTime timestamp when the file was uploaded or accessed
   * @param isDirectory true if this represents a directory
   */
  public FileInfo(
      String fileId,
      String name,
      String path,
      long size,
      LocalDateTime uploadTime,
      boolean isDirectory) {
    this.fileId = fileId;
    this.name = name;
    this.path = path;
    this.size = size;
    this.uploadTime = uploadTime;
    this.isDirectory = isDirectory;
  }

  /**
   * Creates a new file info instance for a file (not directory).
   *
   * @param fileId unique identifier for the file
   * @param name file name
   * @param path full path to the file
   * @param size file size in bytes
   * @param uploadTime timestamp when the file was uploaded or accessed
   */
  public FileInfo(String fileId, String name, String path, long size, LocalDateTime uploadTime) {
    this(fileId, name, path, size, uploadTime, false);
  }

  public String getFileId() {
    return fileId;
  }

  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public LocalDateTime getUploadTime() {
    return uploadTime;
  }

  public void setUploadTime(LocalDateTime uploadTime) {
    this.uploadTime = uploadTime;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public void setDirectory(boolean directory) {
    isDirectory = directory;
  }

  /** Builder class for creating FileInfo instances. */
  public static class Builder {
    private String fileId;
    private String name;
    private String path;
    private long size;
    private LocalDateTime uploadTime;
    private boolean isDirectory;

    public Builder fileId(String fileId) {
      this.fileId = fileId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder path(String path) {
      this.path = path;
      return this;
    }

    public Builder size(long size) {
      this.size = size;
      return this;
    }

    public Builder uploadTime(LocalDateTime uploadTime) {
      this.uploadTime = uploadTime;
      return this;
    }

    public Builder directory(boolean isDirectory) {
      this.isDirectory = isDirectory;
      return this;
    }

    public FileInfo build() {
      return new FileInfo(fileId, name, path, size, uploadTime, isDirectory);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
