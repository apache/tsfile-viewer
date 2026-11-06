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
 * usePagedList - offset/limit 分页列表的滚动加载状态机。
 *
 * 契约来自后端 /api/meta/{fileId}/{row-groups|chunks}：
 * { items, total, offset, limit, hasMore }。
 * hasMore 为 false 之后不再发起任何请求。
 */
import type { PagedResponse } from "@/api/tsfile/types";

import { computed, ref, shallowRef } from "vue";

export type PageFetcher<T> = (offset: number, limit: number) => Promise<PagedResponse<T>>;

export interface PagedListOptions {
  /** 每页条数，默认 50 */
  pageSize?: number;
}

export function usePagedList<T>(fetchPage: PageFetcher<T>, options: PagedListOptions = {}) {
  const pageSize = options.pageSize ?? 50;

  const items = shallowRef<T[]>([]);
  const total = ref(0);
  const hasMore = ref(false);
  const loading = ref(false);
  const error = ref<null | string>(null);
  /** 已加载条数，等于下一次请求的 offset */
  const loadedCount = computed(() => items.value.length);

  // 切换文件时递增，用于丢弃上一个文件迟到的响应
  let generation = 0;
  let nextOffset = 0;

  async function loadMore(): Promise<void> {
    if (loading.value || !hasMore.value) return;
    const requestGeneration = generation;
    loading.value = true;
    error.value = null;
    try {
      const page = await fetchPage(nextOffset, pageSize);
      if (requestGeneration !== generation) return;
      items.value = [...items.value, ...page.items];
      nextOffset = page.offset + page.items.length;
      total.value = page.total;
      // 服务端声称还有下一页却返回空页时也必须停下，否则滚动会无限重试
      hasMore.value = page.hasMore && page.items.length > 0;
    } catch (error_: unknown) {
      if (requestGeneration !== generation) return;
      error.value = error_ instanceof Error ? error_.message : String(error_);
      // 出错后停止自动加载，改由用户点重试，避免滚动一次就失败一次
      hasMore.value = false;
    } finally {
      if (requestGeneration === generation) loading.value = false;
    }
  }

  /** 切换文件或重新进入：清空状态并立即拉第一页 */
  function reset(): void {
    generation += 1;
    items.value = [];
    total.value = 0;
    nextOffset = 0;
    hasMore.value = true;
    loading.value = false;
    error.value = null;
    void loadMore();
  }

  /** 失败后由用户手动重试 */
  function retry(): void {
    if (loading.value) return;
    error.value = null;
    hasMore.value = true;
    void loadMore();
  }

  return { items, total, hasMore, loading, error, loadedCount, loadMore, reset, retry, pageSize };
}

/**
 * 顺序拉取有限页，用于构建长度可控的选项列表（例如设备下拉）。
 *
 * 不做全量拉取：最多 maxPages 页，或服务端没有更多为止，二者先到者为准。
 */
export async function readPagedRange<T>(
  fetchPage: PageFetcher<T>,
  options: { pageSize: number; maxPages: number },
): Promise<{ items: T[]; total: number; complete: boolean }> {
  const collected: T[] = [];
  let offset = 0;
  let total = 0;
  let complete = false;

  for (let page = 0; page < options.maxPages; page += 1) {
    const response = await fetchPage(offset, options.pageSize);
    total = response.total;
    collected.push(...response.items);
    offset = response.offset + response.items.length;
    if (!response.hasMore || response.items.length === 0) {
      complete = true;
      break;
    }
  }

  return { items: collected, total, complete };
}
