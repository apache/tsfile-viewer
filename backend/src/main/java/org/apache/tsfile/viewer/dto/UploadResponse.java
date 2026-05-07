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
 * DTO for file upload response.
 *
 * <p>Returned after a successful TsFile upload, containing the generated fileId and upload
 * metadata.
 *
 * <p>Validates: Requirement 1.4, 8.2 (File upload response)
 */
public class UploadResponse {

  private String fileId;
  private String fileName;
  private long fileSize;
  private LocalDateTime uploadTime;

  /** Default constructor for JSON deserialization. */
  public UploadResponse() {}

  /**
   * Creates a new upload response.
   *
   * @param fileId unique identifier generated for the uploaded file
   * @param fileName original name of the uploaded file
   * @param fileSize size of the uploaded file in bytes
   * @param uploadTime timestamp when the file was uploaded
   */
  public UploadResponse(String fileId, String fileName, long fileSize, LocalDateTime uploadTime) {
    this.fileId = fileId;
    this.fileName = fileName;
    this.fileSize = fileSize;
    this.uploadTime = uploadTime;
  }

  public String getFileId() {
    return fileId;
  }

  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public long getFileSize() {
    return fileSize;
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public LocalDateTime getUploadTime() {
    return uploadTime;
  }

  public void setUploadTime(LocalDateTime uploadTime) {
    this.uploadTime = uploadTime;
  }

  /** Builder class for creating UploadResponse instances. */
  public static class Builder {
    private String fileId;
    private String fileName;
    private long fileSize;
    private LocalDateTime uploadTime;

    public Builder fileId(String fileId) {
      this.fileId = fileId;
      return this;
    }

    public Builder fileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder fileSize(long fileSize) {
      this.fileSize = fileSize;
      return this;
    }

    public Builder uploadTime(LocalDateTime uploadTime) {
      this.uploadTime = uploadTime;
      return this;
    }

    public UploadResponse build() {
      return new UploadResponse(fileId, fileName, fileSize, uploadTime);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
