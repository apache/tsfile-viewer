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

import type { TsFileMetadata } from "@/api/tsfile/types";

/**
 * Meta Store - 元数据状态管理
 */
import { ref } from "vue";

import { defineStore } from "pinia";

export const useMetaStore = defineStore("tsfile-meta", () => {
  const metadataCache = ref<Map<string, TsFileMetadata>>(new Map());
  const loading = ref<boolean>(false);
  const error = ref<null | string>(null);

  function setMetadata(fileId: string, metadata: TsFileMetadata) {
    metadataCache.value.set(fileId, metadata);
  }

  function getMetadata(fileId: string): TsFileMetadata | undefined {
    return metadataCache.value.get(fileId);
  }

  function hasMetadata(fileId: string): boolean {
    return metadataCache.value.has(fileId);
  }

  function clearMetadata(fileId: string) {
    metadataCache.value.delete(fileId);
  }

  function clearAll() {
    metadataCache.value.clear();
  }

  function setLoading(isLoading: boolean) {
    loading.value = isLoading;
  }

  function setError(errorMessage: null | string) {
    error.value = errorMessage;
  }

  return {
    metadataCache,
    loading,
    error,
    setMetadata,
    getMetadata,
    hasMetadata,
    clearMetadata,
    clearAll,
    setLoading,
    setError,
  };
});
