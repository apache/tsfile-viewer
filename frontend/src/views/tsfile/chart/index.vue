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
import { Alert, Button } from "antdv-next";
import { dataApi, metaApi } from "@/api/tsfile";
import ChartPanel from "@/components/tsfile/ChartPanel.vue";
import TableFilterPanel from "@/components/tsfile/TableFilterPanel.vue";
import TreeFilterPanel from "@/components/tsfile/TreeFilterPanel.vue";
import { useFileStore } from "@/stores/tsfile/file";

const route = useRoute();
const router = useRouter();
const fileStore = useFileStore();
const { t } = useI18n();

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
  try {
    const decoded = atob(fileId.value);
    return decoded.split('/').pop() || fileId.value;
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
    const filePath = atob(fileId.value);
    fileStore.setScanTarget(filePath, 'file', true);
    router.push('/tsfile/scan');
  } catch {
    router.push('/tsfile/scan');
  }
}
</script>

<template>
  <div class="flex flex-col" style="height: calc(100vh - 80px)">
    <div class="flex items-center justify-between mb-3 flex-shrink-0">
      <div>
        <h2 class="text-xl font-bold">{{ t("tsfile.chart.title") }}</h2>
        <p class="text-sm text-gray-500">{{ displayFileName }}</p>
      </div>
      <div class="flex gap-2">
        <Button @click="goBack">{{ t("tsfile.common.back") }}</Button>
        <Button @click="goToMetadata">{{ t("tsfile.metadata.title") }}</Button>
        <Button type="primary" @click="goToDataPreview">{{ t("tsfile.data.title") }}</Button>
      </div>
    </div>
    <template v-if="metaError">
      <Alert type="error" show-icon :message="t('tsfile.error.loadFailed')" :description="metaError" class="mb-3">
        <template #action>
          <Button size="small" type="primary" danger @click="goToQuickScan">{{ t('tsfile.scan.quickScan') }}</Button>
        </template>
      </Alert>
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
