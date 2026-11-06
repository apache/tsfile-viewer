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

import type { Chunk, PagedResponse, RowGroup, TsFileMetadata } from "./types";
import { apiClient } from "../request";

/**
 * 元数据摘要。
 *
 * 注意：响应中的 rowGroups / chunks 恒为空数组（大文件的明细会撑爆后端内存），
 * 行数看 rowGroupCount / chunkCount，明细走 getRowGroupsPage / getChunksPage。
 */
export function getMetadata(fileId: string) {
  return apiClient.get<unknown, TsFileMetadata>(`/meta/${fileId}`);
}

/** RowGroup 明细分页 */
export function getRowGroupsPage(fileId: string, offset = 0, limit = 50) {
  return apiClient.get<unknown, PagedResponse<RowGroup>>(`/meta/${fileId}/row-groups`, {
    params: { offset, limit },
  });
}

/** Chunk 明细分页 */
export function getChunksPage(fileId: string, offset = 0, limit = 50) {
  return apiClient.get<unknown, PagedResponse<Chunk>>(`/meta/${fileId}/chunks`, {
    params: { offset, limit },
  });
}
