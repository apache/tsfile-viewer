<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

<script setup lang="ts">
import type { DataPreviewRequest, DataRow, TsFileMetadata } from "@/api/tsfile/types";
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { dataApi, metaApi } from "@/api/tsfile";
import DataTable from "@/components/tsfile/DataTable.vue";
import TableFilterPanel from "@/components/tsfile/TableFilterPanel.vue";
import TreeFilterPanel from "@/components/tsfile/TreeFilterPanel.vue";
import PageHeader from "@/components/layout/PageHeader.vue";
import { useFileStore } from "@/stores/tsfile/file";
import { normalizeToMs } from "@/utils/timestamp";
import { decodeFileId } from "@/utils/fileId";

const route = useRoute();
const router = useRouter();
const fileStore = useFileStore();
const { t } = useI18n();

// 页面刷新时从 localStorage 恢复 currentFile，必须在 watcher (immediate) 之前执行
fileStore.restoreCurrentFile();

const fileId = computed(() => route.params.fileId as string);
const displayFileName = computed(() => {
  if (fileStore.currentFileName) return fileStore.currentFileName;
  // Uploaded-file fileId 是 UUID（最多 32 位 hex），不是 base64 编码的路径，
  // 对其调用 decodeFileId 会产生乱码，直接返回 fileId 让用户知道当前文件标识。
  if (/^[0-9a-fA-F]{8,32}$/.test(fileId.value)) return fileId.value;
  try {
    const decoded = decodeFileId(fileId.value);
    // split 同时支持 Unix 和 Windows 路径分隔符
    return decoded.split(/[/\\]/).pop() || fileId.value;
  } catch {
    return fileId.value;
  }
});
const dataRows = ref<DataRow[]>([]);
const currentOffset = ref(0);
const currentLimit = ref(100);
const hasMore = ref(false);
const loading = ref(false);
const error = ref<string | null>(null);
const warnings = ref<string[]>([]);
const currentFilters = ref<Record<string, unknown>>({});
const metadata = ref<TsFileMetadata | null>(null);
const metaError = ref<string | null>(null);

const tagColumnNames = computed(() => {
  if (!metadata.value?.tables || metadata.value.tables.length === 0) return [];
  const tagNames = new Set<string>();
  for (const table of metadata.value.tables) {
    for (const col of table.tagColumns) {
      if (col.name) tagNames.add(col.name);
    }
  }
  return [...tagNames];
});

const isTableModel = computed(() => metadata.value?.tables && metadata.value.tables.length > 0);

watch(
  fileId,
  (newId, oldId) => {
    if (newId && newId !== oldId) {
      dataRows.value = [];
      currentOffset.value = 0;
      hasMore.value = false;
      error.value = null;
      currentFilters.value = {};
      metadata.value = null;
      loadMetadata();
    }
  },
  { immediate: true },
);

async function loadMetadata() {
  if (!fileId.value) return;
  loading.value = true;
  metaError.value = null;
  try {
    metadata.value = (await metaApi.getMetadata(fileId.value)) as TsFileMetadata;
    // Auto-trigger initial query after metadata loads
    loadData({ limit: currentLimit.value, offset: 0 });
  } catch (e: unknown) {
    metaError.value = e instanceof Error ? e.message : "Failed to load metadata";
    loading.value = false;
  }
}

async function loadData(filters: Record<string, unknown>) {
  loading.value = true;
  error.value = null;
  try {
    const request: DataPreviewRequest = {
      fileId: fileId.value,
      limit: currentLimit.value,
      offset: currentOffset.value,
      ...filters,
    };
    const response = await dataApi.previewData(request);
    dataRows.value = response.data;
    currentOffset.value = response.offset;
    currentLimit.value = response.limit;
    hasMore.value = response.hasMore;
    warnings.value = response.warnings ?? [];
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : "Failed to load data";
  } finally {
    loading.value = false;
  }
}

function handleFilterChange(filters: Record<string, unknown>) {
  currentFilters.value = filters;
  currentOffset.value = 0;
  loadData({ ...filters, limit: currentLimit.value, offset: 0 });
}
function handlePageChange(page: number) {
  currentOffset.value = (page - 1) * currentLimit.value;
  loadData({ ...currentFilters.value, limit: currentLimit.value, offset: currentOffset.value });
}
function handleLimitChange(newLimit: number) {
  currentLimit.value = newLimit;
  currentOffset.value = 0;
  loadData({ ...currentFilters.value, limit: newLimit, offset: 0 });
}
function handleExport(format: "csv" | "json") {
  if (format === "csv") exportCSV();
  else exportJSON();
}
function exportCSV() {
  const columns = new Set<string>();
  for (const row of dataRows.value) {
    for (const key of Object.keys(row.measurements)) columns.add(key);
  }
  const measurementCols = [...columns].sort();
  const headers = ["Timestamp", "Device", ...measurementCols];
  const rows = dataRows.value.map((row) =>
    [
      new Date(normalizeToMs(row.timestamp)).toISOString(),
      row.device,
      ...measurementCols.map((col) => row.measurements[col] ?? ""),
    ].join(","),
  );
  downloadFile([headers.join(","), ...rows].join("\n"), "data.csv", "text/csv");
}
function exportJSON() {
  downloadFile(JSON.stringify(dataRows.value, null, 2), "data.json", "application/json");
}
function downloadFile(content: string, filename: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
function goToChart() {
  router.push(`/tsfile/chart/${fileId.value}`);
}
function goToMetadata() {
  router.push(`/tsfile/meta/${fileId.value}`);
}
function goBack() {
  router.push("/tsfile/files");
}
function goToQuickScan() {
  try {
    const filePath = decodeFileId(fileId.value);
    fileStore.setScanTarget(filePath, 'file', true);
    router.push('/tsfile/scan');
  } catch {
    router.push('/tsfile/scan');
  }
}
</script>

<template>
  <div class="flex flex-col h-full">
    <PageHeader :title="t('tsfile.data.title')" :subtitle="displayFileName">
      <template #actions>
        <el-button @click="goBack">{{ t("tsfile.common.back") }}</el-button>
        <el-button @click="goToMetadata">{{ t("tsfile.metadata.title") }}</el-button>
        <el-button type="primary" @click="goToChart">{{ t("tsfile.chart.title") }}</el-button>
      </template>
    </PageHeader>
    <template v-if="metaError">
      <div class="tc-panel mb-3">
        <div class="flex items-start justify-between gap-3 p-4">
          <div class="min-w-0">
            <p class="font-medium text-danger">{{ t("tsfile.error.loadFailed") }}</p>
            <p class="mt-1 text-sm text-text-body">{{ metaError }}</p>
          </div>
          <el-button size="small" type="danger" class="flex-shrink-0" @click="goToQuickScan">
            {{ t("tsfile.scan.quickScan") }}
          </el-button>
        </div>
      </div>
    </template>
    <template v-else>
    <div class="flex-shrink-0">
      <TableFilterPanel v-if="isTableModel" :file-id="fileId" @change="handleFilterChange" />
      <TreeFilterPanel
        v-else-if="metadata && !isTableModel"
        :file-id="fileId"
        @change="handleFilterChange"
      />
    </div>
    <el-alert
      v-if="warnings.length > 0"
      type="warning"
      show-icon
      :closable="false"
      :title="t('tsfile.data.dataReadWarning')"
      class="mt-3 flex-shrink-0"
    >
      <ul class="m-0 pl-4">
        <li v-for="(w, i) in warnings" :key="i">{{ w }}</li>
      </ul>
    </el-alert>
    <div class="flex-1 mt-3 min-h-0">
      <DataTable
        :data="dataRows"
        :offset="currentOffset"
        :limit="currentLimit"
        :has-more="hasMore"
        :loading="loading"
        :error="error"
        :tag-columns="tagColumnNames"
        @page-change="handlePageChange"
        @limit-change="handleLimitChange"
        @export="handleExport"
      />
    </div>
    </template>
  </div>
</template>
