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
import { Alert, Button } from "antdv-next";
import { dataApi, metaApi } from "@/api/tsfile";
import DataTable from "@/components/tsfile/DataTable.vue";
import TableFilterPanel from "@/components/tsfile/TableFilterPanel.vue";
import TreeFilterPanel from "@/components/tsfile/TreeFilterPanel.vue";
import { useFileStore } from "@/stores/tsfile/file";

const route = useRoute();
const router = useRouter();
const fileStore = useFileStore();
const { t } = useI18n();

const fileId = computed(() => route.params.fileId as string);
const displayFileName = computed(() => {
  if (fileStore.currentFileName) return fileStore.currentFileName;
  try {
    const decoded = atob(fileId.value);
    return decoded.split('/').pop() || fileId.value;
  } catch {
    return fileId.value;
  }
});
const dataRows = ref<DataRow[]>([]);
const total = ref(0);
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
      total.value = 0;
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
    total.value = response.total;
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
      new Date(row.timestamp).toISOString(),
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
    const filePath = atob(fileId.value);
    fileStore.setScanTarget(filePath, 'file', true);
    router.push('/tsfile/scan');
  } catch {
    router.push('/tsfile/scan');
  }
}
</script>

<template>
  <div class="flex flex-col h-full">
    <div class="flex items-center justify-between mb-3 flex-shrink-0">
      <div>
        <h2 class="text-xl font-bold">{{ t("tsfile.data.title") }}</h2>
        <p class="text-gray-500 text-sm truncate max-w-lg">{{ displayFileName }}</p>
      </div>
      <div class="flex gap-2">
        <Button @click="goBack">{{ t("tsfile.common.back") }}</Button>
        <Button @click="goToMetadata">{{ t("tsfile.metadata.title") }}</Button>
        <Button type="primary" @click="goToChart">{{ t("tsfile.chart.title") }}</Button>
      </div>
    </div>
    <template v-if="metaError">
      <Alert type="error" show-icon :message="t('tsfile.error.loadFailed')" :description="metaError" class="mb-3">
        <template #action>
          <Button size="small" type="primary" danger @click="goToQuickScan">{{ t('tsfile.scan.quickScan') }}</Button>
        </template>
      </Alert>
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
    <Alert
      v-if="warnings.length > 0"
      type="warning"
      show-icon
      :message="t('tsfile.data.dataReadWarning')"
      class="mt-3 flex-shrink-0"
    >
      <template #description>
        <ul class="m-0 pl-4">
          <li v-for="(w, i) in warnings" :key="i">{{ w }}</li>
        </ul>
      </template>
    </Alert>
    <div class="flex-1 mt-3 min-h-0">
      <DataTable
        :data="dataRows"
        :total="total"
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
