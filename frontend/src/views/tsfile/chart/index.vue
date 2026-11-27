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
import FileViewNav from "@/components/tsfile/FileViewNav.vue";
import { useFileStore } from "@/stores/tsfile/file";
import { decodeFileId } from "@/utils/fileId";
import { formatFileSize } from "@/utils/formatSize";
import { MAX_CHART_SERIES } from "@/utils/chartLimits";

const route = useRoute();
const router = useRouter();
const fileStore = useFileStore();
const { t } = useI18n();

// 先取到当前路由的 fileId：恢复出来的名字只有在 fileId 一致时才允许使用
const fileId = computed(() => route.params.fileId as string);

// 页面刷新时从 localStorage 恢复 currentFile，必须在 watcher (immediate) 之前执行
fileStore.restoreCurrentFile(fileId.value);
const chartSeries = ref<ChartSeries[]>([]);
const timeRange = ref<TimeRange | undefined>(undefined);
const totalPoints = ref(0);
const downsampled = ref(false);
const loading = ref(false);
const error = ref<string | null>(null);
const currentFilters = ref<Record<string, unknown>>({});
const metadata = ref<TsFileMetadata | null>(null);
/** 页头展示的文件大小（字节），由元数据接口带回来 */
const fileSize = computed(() => metadata.value?.fileSize ?? 0);
const metaError = ref<string | null>(null);
/** 选中的序列超过 MAX_CHART_SERIES 时，被挡掉没有请求的数量 */
const omittedSeries = ref(0);

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
      omittedSeries.value = 0;
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
    const selected = (filters.measurements as string[] | undefined) ?? [];
    // 请求阶段先只带前 MAX_CHART_SERIES 个字段；真正的折线条数要到响应里才知道，见下方截断。
    const measurements = selected.slice(0, MAX_CHART_SERIES);
    if (measurements.length === 0) {
      chartSeries.value = [];
      omittedSeries.value = 0;
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
    // 后端按 device×measurement 展开，一个文件可能有上千个设备，返回的序列数远多于所选字段数。
    // 只画前 MAX_CHART_SERIES 条，并把「没发出去的字段 + 没画出来的序列」一起报给 ChartPanel。
    const allSeries = response.series ?? [];
    const drawnSeries = allSeries.slice(0, MAX_CHART_SERIES);
    chartSeries.value = drawnSeries;
    omittedSeries.value =
      selected.length - measurements.length + (allSeries.length - drawnSeries.length);
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
    <FileViewNav :file-id="fileId" active="chart" />
    <div class="mb-4 flex flex-wrap items-center gap-x-5 gap-y-1 text-sm text-text-body">
      <span>{{ t("tsfile.common.fileInfo", { name: displayFileName }) }}</span>
      <span v-if="fileSize > 0" class="tnum">
        {{ t("tsfile.common.fileSize", { size: formatFileSize(fileSize) }) }}
      </span>
    </div>
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
        <TableFilterPanel
          v-if="isTableModel"
          :key="fileId"
          :file-id="fileId"
          chart-mode
          @change="handleFilterChange"
        />
        <TreeFilterPanel
          v-else-if="metadata && !isTableModel"
          :key="fileId"
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
          :omitted-series="omittedSeries"
        />
      </div>
    </div>
  </div>
</template>
