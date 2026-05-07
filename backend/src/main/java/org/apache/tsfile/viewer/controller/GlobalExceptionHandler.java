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

import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.tsfile.exception.NotCompatibleTsFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import org.apache.tsfile.viewer.dto.ErrorResponse;
import org.apache.tsfile.viewer.dto.ValidationError;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.EmptyFileException;
import org.apache.tsfile.viewer.exception.FileTooLargeException;
import org.apache.tsfile.viewer.exception.InvalidFileException;
import org.apache.tsfile.viewer.exception.QueryTimeoutException;
import org.apache.tsfile.viewer.exception.TaskNotFoundException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Global exception handler for the TsFile Viewer application.
 *
 * <p>Provides consistent error responses across all REST endpoints using {@link ErrorResponse} DTO.
 * Handles both application-specific exceptions and common Spring exceptions.
 *
 * <p>Validates: Requirement 7.1-7.6 (Error handling)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Handles TsFileNotFoundException - returns 404 Not Found.
   *
   * <p>Validates: Requirement 7.1 (File not found error handling)
   */
  @ExceptionHandler(TsFileNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleTsFileNotFoundException(
      TsFileNotFoundException ex, HttpServletRequest request) {
    logger.warn("File not found: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
  }

  /**
   * Handles TaskNotFoundException - returns 404 Not Found.
   *
   * <p>Validates: Requirement 9.1 (Scan task not found handling)
   */
  @ExceptionHandler(TaskNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleTaskNotFoundException(
      TaskNotFoundException ex, HttpServletRequest request) {
    logger.warn("Scan task not found: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
  }

  /**
   * Handles standard FileNotFoundException - returns 404 Not Found.
   *
   * <p>Validates: Requirement 7.1 (File not found error handling)
   */
  @ExceptionHandler(FileNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleFileNotFoundException(
      FileNotFoundException ex, HttpServletRequest request) {
    logger.warn("File not found: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
  }

  /**
   * Handles InvalidFileException - returns 400 Bad Request.
   *
   * <p>Validates: Requirement 7.2 (Invalid file error handling)
   */
  @ExceptionHandler(InvalidFileException.class)
  public ResponseEntity<ErrorResponse> handleInvalidFileException(
      InvalidFileException ex, HttpServletRequest request) {
    logger.warn("Invalid file: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * Handles EmptyFileException - returns 400 Bad Request.
   *
   * <p>This exception is thrown when a TsFile is valid but contains no data. Returns a friendly
   * error message to help users understand the issue.
   */
  @ExceptionHandler(EmptyFileException.class)
  public ResponseEntity<ErrorResponse> handleEmptyFileException(
      EmptyFileException ex, HttpServletRequest request) {
    logger.warn("Empty file: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * Handles NotCompatibleTsFileException - returns 400 Bad Request.
   *
   * <p>This exception is thrown when a TsFile format is incompatible or corrupted. Returns a
   * friendly error message to help users understand the issue.
   */
  @ExceptionHandler(NotCompatibleTsFileException.class)
  public ResponseEntity<ErrorResponse> handleNotCompatibleTsFileException(
      NotCompatibleTsFileException ex, HttpServletRequest request) {
    logger.warn("Incompatible TsFile format: {}", ex.getMessage());

    String friendlyMessage =
        "The file format is incompatible or corrupted. Please ensure it is a valid TsFile created with a compatible version.";

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(friendlyMessage)
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * Handles validation errors from @Valid annotations - returns 400 Bad Request.
   *
   * <p>Validates: Requirement 7.3 (Validation errors)
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    logger.warn("Validation failed: {}", ex.getMessage());

    List<ValidationError> validationErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(this::mapFieldError)
            .collect(Collectors.toList());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message("Validation failed for request parameters")
            .path(request.getRequestURI())
            .validationErrors(validationErrors)
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * Handles missing request parameters - returns 400 Bad Request.
   *
   * <p>Validates: Requirement 7.3 (Validation errors)
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameterException(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    logger.warn("Missing parameter: {}", ex.getMessage());

    ValidationError validationError =
        new ValidationError(ex.getParameterName(), "Parameter is required", null);

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message("Required parameter '" + ex.getParameterName() + "' is missing")
            .path(request.getRequestURI())
            .validationErrors(List.of(validationError))
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * Handles type mismatch errors - returns 400 Bad Request.
   *
   * <p>Validates: Requirement 7.3 (Validation errors)
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatchException(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    logger.warn("Type mismatch: {}", ex.getMessage());

    String message =
        String.format(
            "Parameter '%s' should be of type %s",
            ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

    ValidationError validationError = new ValidationError(ex.getName(), message, ex.getValue());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(message)
            .path(request.getRequestURI())
            .validationErrors(List.of(validationError))
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * Handles AccessDeniedException - returns 403 Forbidden.
   *
   * <p>Validates: Requirement 7.6 (Directory whitelist security)
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDeniedException(
      AccessDeniedException ex, HttpServletRequest request) {
    logger.warn("Access denied: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.FORBIDDEN.value())
            .error("Forbidden")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
  }

  /**
   * Handles FileTooLargeException - returns 413 Payload Too Large.
   *
   * <p>Validates: Requirement 7.5 (Upload size limit error handling)
   */
  @ExceptionHandler(FileTooLargeException.class)
  public ResponseEntity<ErrorResponse> handleFileTooLargeException(
      FileTooLargeException ex, HttpServletRequest request) {
    logger.warn("File too large: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
            .error("Payload Too Large")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
  }

  /**
   * Handles Spring's MaxUploadSizeExceededException - returns 413 Payload Too Large.
   *
   * <p>Validates: Requirement 7.5 (Upload size limit error handling)
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException ex, HttpServletRequest request) {
    logger.warn("Upload size exceeded: {}", ex.getMessage());

    String message =
        String.format("Upload exceeds maximum allowed size of %d bytes", ex.getMaxUploadSize());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
            .error("Payload Too Large")
            .message(message)
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
  }

  /**
   * Handles QueryTimeoutException - returns 504 Gateway Timeout.
   *
   * <p>Validates: Requirement 5.5 (Query timeout handling)
   */
  @ExceptionHandler(QueryTimeoutException.class)
  public ResponseEntity<ErrorResponse> handleQueryTimeoutException(
      QueryTimeoutException ex, HttpServletRequest request) {
    logger.warn("Query timeout: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.GATEWAY_TIMEOUT.value())
            .error("Gateway Timeout")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
  }

  /**
   * Handles standard TimeoutException - returns 504 Gateway Timeout.
   *
   * <p>Validates: Requirement 5.5 (Query timeout handling)
   */
  @ExceptionHandler(TimeoutException.class)
  public ResponseEntity<ErrorResponse> handleTimeoutException(
      TimeoutException ex, HttpServletRequest request) {
    logger.warn("Timeout: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.GATEWAY_TIMEOUT.value())
            .error("Gateway Timeout")
            .message("Request timed out: " + ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
  }

  /**
   * Handles IllegalArgumentException - returns 400 Bad Request.
   *
   * <p>Validates: Requirement 7.3 (Validation errors)
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
      IllegalArgumentException ex, HttpServletRequest request) {
    logger.warn("Illegal argument: {}", ex.getMessage());

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * Handles all other unhandled exceptions - returns 500 Internal Server Error.
   *
   * <p>Logs detailed error information for debugging while returning a generic message to users.
   *
   * <p>Validates: Requirement 7.4 (Server error handling)
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(
      Exception ex, HttpServletRequest request) {
    logger.error("Unexpected error occurred", ex);

    ErrorResponse response =
        ErrorResponse.builder()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred. Please try again later.")
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  /**
   * Maps a Spring FieldError to a ValidationError DTO.
   *
   * @param fieldError the field error from validation
   * @return the mapped ValidationError
   */
  private ValidationError mapFieldError(FieldError fieldError) {
    return new ValidationError(
        fieldError.getField(), fieldError.getDefaultMessage(), fieldError.getRejectedValue());
  }
}
