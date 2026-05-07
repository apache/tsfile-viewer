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

package org.apache.tsfile.viewer.exception;

/**
 * Exception thrown when a requested TsFile or resource is not found.
 *
 * <p>Results in HTTP 404 Not Found response.
 *
 * <p>Validates: Requirement 7.1 (File not found error handling)
 */
public class TsFileNotFoundException extends RuntimeException {

  private final String fileId;

  /**
   * Creates a new TsFileNotFoundException with a message.
   *
   * @param message the error message
   */
  public TsFileNotFoundException(String message) {
    super(message);
    this.fileId = null;
  }

  /**
   * Creates a new TsFileNotFoundException with a message and file ID.
   *
   * @param message the error message
   * @param fileId the file ID that was not found
   */
  public TsFileNotFoundException(String message, String fileId) {
    super(message);
    this.fileId = fileId;
  }

  /**
   * Creates a new TsFileNotFoundException with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public TsFileNotFoundException(String message, Throwable cause) {
    super(message, cause);
    this.fileId = null;
  }

  /**
   * Gets the file ID that was not found.
   *
   * @return the file ID, or null if not specified
   */
  public String getFileId() {
    return fileId;
  }
}
