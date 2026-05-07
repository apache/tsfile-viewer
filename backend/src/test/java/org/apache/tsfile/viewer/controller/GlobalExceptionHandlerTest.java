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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.util.concurrent.TimeoutException;

import org.apache.tsfile.exception.NotCompatibleTsFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.apache.tsfile.viewer.dto.ErrorResponse;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.EmptyFileException;
import org.apache.tsfile.viewer.exception.FileTooLargeException;
import org.apache.tsfile.viewer.exception.InvalidFileException;
import org.apache.tsfile.viewer.exception.QueryTimeoutException;
import org.apache.tsfile.viewer.exception.TaskNotFoundException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for GlobalExceptionHandler.
 *
 * <p>Validates: Requirement 7.1-7.6 (Error handling)
 */
class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
    request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/test");
  }

  @Nested
  @DisplayName("404 Not Found Tests")
  class NotFoundTests {

    @Test
    @DisplayName("Should return 404 for TsFileNotFoundException")
    void handleTsFileNotFoundException_returns404() {
      TsFileNotFoundException ex = new TsFileNotFoundException("File not found", "file-123");

      ResponseEntity<ErrorResponse> response = handler.handleTsFileNotFoundException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(404);
      assertThat(response.getBody().getError()).isEqualTo("Not Found");
      assertThat(response.getBody().getMessage()).isEqualTo("File not found");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
      assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for standard FileNotFoundException")
    void handleFileNotFoundException_returns404() {
      FileNotFoundException ex = new FileNotFoundException("File does not exist");

      ResponseEntity<ErrorResponse> response = handler.handleFileNotFoundException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(404);
      assertThat(response.getBody().getError()).isEqualTo("Not Found");
      assertThat(response.getBody().getMessage()).isEqualTo("File does not exist");
    }

    @Test
    @DisplayName("Should return 404 for TaskNotFoundException")
    void handleTaskNotFoundException_returns404() {
      TaskNotFoundException ex =
          new TaskNotFoundException("Scan task not found: task-123", "task-123");

      ResponseEntity<ErrorResponse> response = handler.handleTaskNotFoundException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(404);
      assertThat(response.getBody().getError()).isEqualTo("Not Found");
      assertThat(response.getBody().getMessage()).isEqualTo("Scan task not found: task-123");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
      assertThat(response.getBody().getTimestamp()).isNotNull();
    }
  }

  @Nested
  @DisplayName("400 Bad Request Tests")
  class BadRequestTests {

    @Test
    @DisplayName("Should return 400 for InvalidFileException")
    void handleInvalidFileException_returns400() {
      InvalidFileException ex = new InvalidFileException("Invalid TsFile format", "test.tsfile");

      ResponseEntity<ErrorResponse> response = handler.handleInvalidFileException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(400);
      assertThat(response.getBody().getError()).isEqualTo("Bad Request");
      assertThat(response.getBody().getMessage()).isEqualTo("Invalid TsFile format");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }

    @Test
    @DisplayName("Should return 400 for IllegalArgumentException")
    void handleIllegalArgumentException_returns400() {
      IllegalArgumentException ex = new IllegalArgumentException("Invalid parameter value");

      ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(400);
      assertThat(response.getBody().getError()).isEqualTo("Bad Request");
      assertThat(response.getBody().getMessage()).isEqualTo("Invalid parameter value");
    }

    @Test
    @DisplayName("Should return 400 for EmptyFileException")
    void handleEmptyFileException_returns400() {
      EmptyFileException ex =
          new EmptyFileException(
              "The TsFile is valid but contains no data. It may be a newly created or empty file.");

      ResponseEntity<ErrorResponse> response = handler.handleEmptyFileException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(400);
      assertThat(response.getBody().getError()).isEqualTo("Bad Request");
      assertThat(response.getBody().getMessage())
          .isEqualTo(
              "The TsFile is valid but contains no data. It may be a newly created or empty file.");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }

    @Test
    @DisplayName("Should return 400 for NotCompatibleTsFileException")
    void handleNotCompatibleTsFileException_returns400() {
      NotCompatibleTsFileException ex =
          new NotCompatibleTsFileException("java.nio.BufferUnderflowException");

      ResponseEntity<ErrorResponse> response =
          handler.handleNotCompatibleTsFileException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(400);
      assertThat(response.getBody().getError()).isEqualTo("Bad Request");
      assertThat(response.getBody().getMessage())
          .isEqualTo(
              "The file format is incompatible or corrupted. Please ensure it is a valid TsFile created with a compatible version.");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }
  }

  @Nested
  @DisplayName("403 Forbidden Tests")
  class ForbiddenTests {

    @Test
    @DisplayName("Should return 403 for AccessDeniedException")
    void handleAccessDeniedException_returns403() {
      AccessDeniedException ex =
          new AccessDeniedException("Access denied: path outside whitelist", "/etc/passwd");

      ResponseEntity<ErrorResponse> response = handler.handleAccessDeniedException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(403);
      assertThat(response.getBody().getError()).isEqualTo("Forbidden");
      assertThat(response.getBody().getMessage())
          .isEqualTo("Access denied: path outside whitelist");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }
  }

  @Nested
  @DisplayName("413 Payload Too Large Tests")
  class PayloadTooLargeTests {

    @Test
    @DisplayName("Should return 413 for FileTooLargeException")
    void handleFileTooLargeException_returns413() {
      FileTooLargeException ex =
          new FileTooLargeException(
              "File size 200MB exceeds maximum allowed size of 100MB", 200_000_000L, 100_000_000L);

      ResponseEntity<ErrorResponse> response = handler.handleFileTooLargeException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(413);
      assertThat(response.getBody().getError()).isEqualTo("Payload Too Large");
      assertThat(response.getBody().getMessage())
          .contains("File size 200MB exceeds maximum allowed size of 100MB");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }
  }

  @Nested
  @DisplayName("504 Gateway Timeout Tests")
  class TimeoutTests {

    @Test
    @DisplayName("Should return 504 for QueryTimeoutException")
    void handleQueryTimeoutException_returns504() {
      QueryTimeoutException ex = new QueryTimeoutException("Query exceeded 30 second timeout", 30);

      ResponseEntity<ErrorResponse> response = handler.handleQueryTimeoutException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(504);
      assertThat(response.getBody().getError()).isEqualTo("Gateway Timeout");
      assertThat(response.getBody().getMessage()).isEqualTo("Query exceeded 30 second timeout");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }

    @Test
    @DisplayName("Should return 504 for standard TimeoutException")
    void handleTimeoutException_returns504() {
      TimeoutException ex = new TimeoutException("Operation timed out");

      ResponseEntity<ErrorResponse> response = handler.handleTimeoutException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(504);
      assertThat(response.getBody().getError()).isEqualTo("Gateway Timeout");
      assertThat(response.getBody().getMessage()).contains("Operation timed out");
    }
  }

  @Nested
  @DisplayName("500 Internal Server Error Tests")
  class InternalServerErrorTests {

    @Test
    @DisplayName("Should return 500 for generic Exception")
    void handleGenericException_returns500() {
      Exception ex = new RuntimeException("Unexpected error");

      ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(500);
      assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
      // Should return generic message, not expose internal details
      assertThat(response.getBody().getMessage())
          .isEqualTo("An unexpected error occurred. Please try again later.");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }
  }

  @Nested
  @DisplayName("Exception Properties Tests")
  class ExceptionPropertiesTests {

    @Test
    @DisplayName("TsFileNotFoundException should store fileId")
    void tsFileNotFoundException_storesFileId() {
      TsFileNotFoundException ex = new TsFileNotFoundException("Not found", "file-123");

      assertThat(ex.getFileId()).isEqualTo("file-123");
      assertThat(ex.getMessage()).isEqualTo("Not found");
    }

    @Test
    @DisplayName("InvalidFileException should store fileName")
    void invalidFileException_storesFileName() {
      InvalidFileException ex = new InvalidFileException("Invalid format", "test.tsfile");

      assertThat(ex.getFileName()).isEqualTo("test.tsfile");
      assertThat(ex.getMessage()).isEqualTo("Invalid format");
    }

    @Test
    @DisplayName("AccessDeniedException should store path")
    void accessDeniedException_storesPath() {
      AccessDeniedException ex = new AccessDeniedException("Access denied", "/etc/passwd");

      assertThat(ex.getPath()).isEqualTo("/etc/passwd");
      assertThat(ex.getMessage()).isEqualTo("Access denied");
    }

    @Test
    @DisplayName("FileTooLargeException should store size information")
    void fileTooLargeException_storesSizeInfo() {
      FileTooLargeException ex = new FileTooLargeException("Too large", 200L, 100L);

      assertThat(ex.getFileSize()).isEqualTo(200L);
      assertThat(ex.getMaxSize()).isEqualTo(100L);
      assertThat(ex.getMessage()).isEqualTo("Too large");
    }

    @Test
    @DisplayName("QueryTimeoutException should store timeout value")
    void queryTimeoutException_storesTimeout() {
      QueryTimeoutException ex = new QueryTimeoutException("Timeout", 30);

      assertThat(ex.getTimeoutSeconds()).isEqualTo(30);
      assertThat(ex.getMessage()).isEqualTo("Timeout");
    }

    @Test
    @DisplayName("TaskNotFoundException should store taskId")
    void taskNotFoundException_storesTaskId() {
      TaskNotFoundException ex = new TaskNotFoundException("Task not found", "task-123");

      assertThat(ex.getTaskId()).isEqualTo("task-123");
      assertThat(ex.getMessage()).isEqualTo("Task not found");
    }

    @Test
    @DisplayName("TaskNotFoundException should allow null taskId")
    void taskNotFoundException_allowsNullTaskId() {
      TaskNotFoundException ex = new TaskNotFoundException("Task not found");

      assertThat(ex.getTaskId()).isNull();
      assertThat(ex.getMessage()).isEqualTo("Task not found");
    }
  }
}
