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

/**
 * TsFile Viewer API Types
 * Migrated from frontend_old/src/api/types.ts
 */

// File Management Types
export interface TreeNode {
  name: string;
  path: string;
  isDirectory: boolean;
  children?: TreeNode[];
  isLoaded?: boolean;
}

export interface UploadResponse {
  fileId: string;
  fileName: string;
  fileSize: number;
  uploadTime: string;
}

export interface FileInfo {
  fileId: string;
  name: string;
  path: string;
  size: number;
  uploadTime: string;
  isDirectory?: boolean;
}

// Metadata Types
export interface TimeRange {
  startTime: number;
  endTime: number;
}

export interface Measurement {
  name: string;
  dataType: string;
  encoding: string;
  compression: string;
  columnCategory?: string; // TAG, FIELD, or ATTRIBUTE
}

export interface TableColumn {
  name: string;
  dataType: string;
  encoding: string;
  compression: string;
  category: "FIELD" | "TAG";
}

export interface Table {
  tableName: string;
  tagColumns: TableColumn[];
  fieldColumns: TableColumn[];
  totalColumns: number;
}

export interface RowGroup {
  index: number;
  device: string;
  startTime: number;
  endTime: number;
  chunkCount: number;
}

export interface Chunk {
  measurement: string;
  offset: number;
  size: number;
  compressionRatio: number;
  device?: string;
  dataType?: string;
  encoding?: string;
  compression?: string;
  startTime?: number;
  endTime?: number;
  numOfPoints?: number;
  minValue?: string;
  maxValue?: string;
}

export interface TsFileMetadata {
  fileId: string;
  version: string;
  timeRange: TimeRange;
  deviceCount: number;
  measurementCount: number;
  rowGroupCount: number;
  chunkCount: number;
  measurements: Measurement[];
  rowGroups: RowGroup[];
  chunks: Chunk[];
  tables?: Table[]; // V4 table model tables
}

// Data Query Types
export interface ValueRange {
  min?: number;
  max?: number;
}

/**
 * Comparison operators for advanced filtering
 */
export type ComparisonOperator =
  | "EQUAL"
  | "GREATER"
  | "GREATER_EQUAL"
  | "LESS"
  | "LESS_EQUAL"
  | "NOT_EQUAL";

/**
 * Logical operators for combining conditions
 */
export type LogicalOperator = "AND" | "OR";

/**
 * Single advanced filter condition
 */
export interface AdvancedCondition {
  id: string; // Frontend unique identifier for v-for :key
  field: string; // Field/measurement name
  operator: ComparisonOperator; // Comparison operator
  value: boolean | number | string; // Value to compare against
  logic: LogicalOperator; // Logical relationship with next condition
}

export interface FilterConditions {
  startTime?: number;
  endTime?: number;
  devices?: string[];
  measurements?: string[];
  valueRange?: ValueRange;
  limit: number;
  offset: number;
  advancedConditions?: AdvancedCondition[];
}

export interface DataRow {
  timestamp: number;
  device: string;
  measurements: Record<string, unknown>;
}

export interface DataPreviewRequest {
  fileId: string;
  startTime?: number;
  endTime?: number;
  devices?: string[];
  measurements?: string[];
  valueRange?: ValueRange;
  limit: number;
  offset: number;
  advancedConditions?: AdvancedCondition[];
}

export interface DataPreviewResponse {
  data: DataRow[];
  total: number;
  limit: number;
  offset: number;
  hasMore: boolean;
  warnings?: string[];
}

// Table Model Query Types
export interface TableDataRequest {
  fileId: string;
  tableName: string;
  devices?: string[];
  fields?: string[];
  startTime?: number;
  endTime?: number;
  limit: number;
  offset: number;
  advancedConditions?: AdvancedCondition[];
}

export interface TableDataResponse {
  data: Record<string, unknown>[];
  total: number;
  limit: number;
  offset: number;
  hasMore: boolean;
  columns: string[];
}

// Chart Data Types
export type AggregationType = "AVG" | "COUNT" | "MAX" | "MIN";

export interface ChartSeries {
  name: string;
  data: number[][];
}

export interface ChartDataRequest {
  fileId: string;
  startTime?: number;
  endTime?: number;
  measurements: string[];
  devices?: string[];
  tableName?: string;
  aggregation?: AggregationType;
  windowSize?: number;
  maxPoints?: number;
}

export interface ChartDataResponse {
  series: ChartSeries[];
  timeRange: TimeRange;
  totalPoints: number;
  downsampled: boolean;
}

// Error Response Type
export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  path: string;
  validationErrors?: ValidationError[];
}

export interface ValidationError {
  field: string;
  message: string;
}
