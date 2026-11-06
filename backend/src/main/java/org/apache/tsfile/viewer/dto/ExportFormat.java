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

/**
 * Downloadable representation of an exported data set.
 *
 * <p>Each constant carries the media type and the download file name, so the controller and the
 * writer in the service layer agree on a single definition.
 */
public enum ExportFormat {
  CSV("csv", "text/csv;charset=UTF-8", "data.csv"),
  JSON("json", "application/json;charset=UTF-8", "data.json");

  private final String parameterValue;
  private final String contentType;
  private final String fileName;

  ExportFormat(String parameterValue, String contentType, String fileName) {
    this.parameterValue = parameterValue;
    this.contentType = contentType;
    this.fileName = fileName;
  }

  /**
   * Resolves the {@code format} request parameter.
   *
   * @param raw the raw parameter value, e.g. {@code csv}; blank means {@link #CSV}
   * @return the matching format
   * @throws IllegalArgumentException if the value matches no supported format (mapped to HTTP 400)
   */
  public static ExportFormat from(String raw) {
    if (raw == null || raw.isBlank()) {
      return CSV;
    }
    String normalized = raw.trim();
    for (ExportFormat format : values()) {
      if (format.parameterValue.equalsIgnoreCase(normalized)) {
        return format;
      }
    }
    throw new IllegalArgumentException(
        "Unsupported export format '" + raw + "'; expected 'csv' or 'json'");
  }

  /** Returns the {@code Content-Type} announced for the download response. */
  public String getContentType() {
    return contentType;
  }

  /** Returns the file name announced in the {@code Content-Disposition} response header. */
  public String getFileName() {
    return fileName;
  }

  /** Returns the {@code Content-Disposition} value that makes browsers save instead of render. */
  public String getContentDisposition() {
    return "attachment; filename=\"" + fileName + "\"";
  }
}
