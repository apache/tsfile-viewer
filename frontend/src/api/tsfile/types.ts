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

/**
 * 分页响应，由 /api/meta/{fileId}/row-groups 与 /api/meta/{fileId}/chunks 共用。
 *
 * items 的元素类型由调用方指定；offset/limit 回显请求参数（limit 可能被服务端收窄），
 * hasMore 是“是否还有下一页”的唯一判据，不要用 offset + items.length < total 自行推算。
 */
export interface PagedResponse<T> {
  items: T[];
  total: number;
  offset: number;
  limit: number;
  hasMore: boolean;
}

/**
 * 单个设备携带的测点。
 *
 * 树模型文件的测点挂在设备下，测点选项应跟随所选设备，而不是取整文件并集。
 */
export interface DeviceMeasurements {
  device: string;
  measurements: Measurement[];
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
  /** 摘要接口恒为空数组，明细请用 metaApi.getRowGroupsPage() 分页拉取 */
  rowGroups: RowGroup[];
  /** 摘要接口恒为空数组，明细请用 metaApi.getChunksPage() 分页拉取 */
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
  /**
   * 时间戳的精确原始数字串。JSON.parse 会把 >2^53 的纳秒时间戳转成有损 double，
   * 该字段在解析前从响应文本中原样保留，用于无损展示原始存储值。
   */
  timestampRaw?: string;
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
