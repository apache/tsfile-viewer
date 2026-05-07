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
 * Health status of a scanned TsFile.
 *
 * <p>Validates: Requirement 2.4 (Severity classification)
 */
public enum HealthStatus {
  /** File is structurally intact and all statistics are consistent. */
  HEALTHY,

  /** File has non-fatal issues such as data read anomalies. */
  WARNING,

  /** File has critical or structural errors. */
  ERROR
}
