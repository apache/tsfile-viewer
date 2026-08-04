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
import type { ChartDataRequest, ChartSeries, TimeRange, TsFileMetadata } from "@/api/tsfile/types";
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { dataApi, metaApi } from "@/api/tsfile";
import ChartPanel from "@/components/tsfile/ChartPanel.vue";
import TableFilterPanel from "@/components/tsfile/TableFilterPanel.vue";
import TreeFilterPanel from "@/components/tsfile/TreeFilterPanel.vue";
import PageHeader from "@/components/layout/PageHeader.vue";
import { useFileStore } from "@/stores/tsfile/file";
import { decodeFileId } from "@/utils/fileId";

const route = useRoute();
const router = useRouter();
const fileStore = useFileStore();
const { t } = useI18n();

// 页面刷新时从 localStorage 恢复 currentFile，必须在 watcher (immediate) 之前执行
fileStore.restoreCurrentFile();

const fileId = computed(() => route.params.fileId as string);
const chartSeries = ref<ChartSeries[]>([]);
const timeRange = ref<TimeRange | undefined>(undefined);
const totalPoints = ref(0);
const downsampled = ref(false);
const loading = ref(false);
const error = ref<string | null>(null);
const currentFilters = ref<Record<string, unknown>>({});
const metadata = ref<TsFileMetadata | null>(null);
const metaError = ref<string | null>(null);

const isTableModel = computed(() => metadata.value?.tables && metadata.value.tables.length > 0);

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

watch(
  fileId,
  (newId, oldId) => {
    if (newId && newId !== oldId) {
      chartSeries.value = [];
      timeRange.value = undefined;
      totalPoints.value = 0;
      downsampled.value = false;
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
  metaError.value = null;
  try {
    metadata.value = (await metaApi.getMetadata(fileId.value)) as TsFileMetadata;
  } catch (e: unknown) {
    metaError.value = e instanceof Error ? e.message : "Failed to load metadata";
  }
}

async function loadChartData(filters: Record<string, unknown>) {
  loading.value = true;
  error.value = null;
  try {
    const measurements = filters.measurements as string[] | undefined;
    if (!measurements || measurements.length === 0) {
      chartSeries.value = [];
      loading.value = false;
      return;
    }
    const request: ChartDataRequest = {
      fileId: fileId.value,
      measurements,
      devices: filters.devices as string[] | undefined,
      tableName: filters.tableName as string | undefined,
      startTime: filters.startTime as number | undefined,
      endTime: filters.endTime as number | undefined,
      maxPoints: 10_000,
    };
    const response = await dataApi.queryChartData(request);
    chartSeries.value = response.series;
    timeRange.value = response.timeRange;
    totalPoints.value = response.totalPoints;
    downsampled.value = response.downsampled;
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : "Failed to load chart data";
  } finally {
    loading.value = false;
  }
}

function handleFilterChange(filters: Record<string, unknown>) {
  currentFilters.value = filters;
  loadChartData(filters);
}
function goToDataPreview() {
  router.push(`/tsfile/data/${fileId.value}`);
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
  <!-- 高度交给父级 flex 布局，不要用 calc(100vh - 常量) 猜页头高度 -->
  <div class="flex h-full flex-col">
    <PageHeader :title="t('tsfile.chart.title')" :subtitle="displayFileName">
      <template #actions>
        <el-button @click="goBack">{{ t("tsfile.common.back") }}</el-button>
        <el-button @click="goToMetadata">{{ t("tsfile.metadata.title") }}</el-button>
        <el-button type="primary" @click="goToDataPreview">{{ t("tsfile.data.title") }}</el-button>
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
    <div v-else class="flex flex-col flex-1 min-h-0 gap-3">
      <div class="flex-shrink-0">
        <TableFilterPanel v-if="isTableModel" :file-id="fileId" chart-mode @change="handleFilterChange" />
        <TreeFilterPanel
          v-else-if="metadata && !isTableModel"
          :file-id="fileId"
          chart-mode
          @change="handleFilterChange"
        />
      </div>
      <div class="flex-1 min-h-0">
        <ChartPanel
          :series="chartSeries"
          :time-range="timeRange"
          :loading="loading"
          :error="error"
          :downsampled="downsampled"
          :total-points="totalPoints"
        />
      </div>
    </div>
  </div>
</template>
