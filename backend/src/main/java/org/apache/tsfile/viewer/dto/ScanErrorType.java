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
 * Types of errors that can be detected during a TsFile health check.
 *
 * <p>Validates: Requirement 2.3 (Five error types identification)
 */
public enum ScanErrorType {
  /** Magic number or version number is invalid — file format is incompatible. */
  FORMAT_INCOMPATIBLE,

  /** ChunkGroup or Chunk markers are abnormal — file structure is corrupt. */
  STRUCTURE_CORRUPT,

  /** Chunk-level statistics do not match aggregated Page-level data. */
  CHUNK_STATISTICS_MISMATCH,

  /** TimeseriesMetadata statistics do not match aggregated ChunkMetadata statistics. */
  TIMESERIES_METADATA_MISMATCH,

  /** An IOException occurred while reading the file. */
  DATA_READ_ERROR
}
