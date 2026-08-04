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
/**
 * TreeFilterPanel - 树模型筛选面板
 * 所有下拉选择器使用 el-select-v2（虚拟化渲染），即使选项数量很大也不会卡顿。
 */
import type { AdvancedCondition, TsFileMetadata } from "@/api/tsfile/types";

import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";

import dayjs from "dayjs";
import { ElSelectV2 } from "element-plus";

import { metaApi } from "@/api/tsfile";
import AdvancedFilterDialog from "@/components/tsfile/AdvancedFilterDialog.vue";

interface Props {
  fileId: string;
  chartMode?: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  change: [filters: Record<string, unknown>];
}>();

const { t } = useI18n();

const loading = ref(true);
const error = ref<string | null>(null);
const metadata = ref<TsFileMetadata | null>(null);

const selectedDevice = ref<string>("");
const selectedMeasurements = ref<string[]>([]);
const timeRange = ref<[Date, Date] | null>(null);

const quickTimeRanges = computed(() => [
  { label: t("tsfile.data.last1h"), value: "1h" },
  { label: t("tsfile.data.last6h"), value: "6h" },
  { label: t("tsfile.data.last24h"), value: "24h" },
  { label: t("tsfile.data.last7d"), value: "7d" },
  { label: t("tsfile.data.last30d"), value: "30d" },
]);
const selectedQuickRange = ref<string | null>(null);

const showAdvancedDialog = ref(false);
const advancedConditions = ref<AdvancedCondition[]>([]);

const deviceOptions = computed(() => {
  if (!metadata.value?.rowGroups) return [];
  const devices = new Set<string>();
  for (const rg of metadata.value.rowGroups) {
    if (rg.device) devices.add(rg.device);
  }
  return [...devices].sort().map((d) => ({ label: d, value: d }));
});

const measurementOptions = computed(() => {
  if (!metadata.value?.measurements) return [];
  return metadata.value.measurements
    .map((m) => m.name)
    .filter(Boolean)
    .sort() as string[];
});

const numericTypes = new Set(['INT32', 'INT64', 'FLOAT', 'DOUBLE', 'BOOLEAN']);

// 图表模式下默认展示的序列数上限（避免一次性绘制数千条折线导致页面卡死）
const CHART_DEFAULT_SERIES = 8;

// 取前 N 个数值测点，作为图表默认序列
function defaultChartMeasurements(): string[] {
  return (metadata.value?.measurements || [])
    .filter((m) => m.name && numericTypes.has(m.dataType?.toUpperCase() || ''))
    .map((m) => m.name)
    .slice(0, CHART_DEFAULT_SERIES);
}

const measurementSelectOptions = computed(() => {
  if (!props.chartMode) {
    return measurementOptions.value.map((m) => ({ label: m, value: m, disabled: false }));
  }
  // In chart mode, disable non-numeric fields
  const measurements = metadata.value?.measurements || [];
  return measurementOptions.value.map((m) => {
    const meta = measurements.find((mm) => mm.name === m);
    const isNumeric = meta ? numericTypes.has(meta.dataType?.toUpperCase() || '') : true;
    return { label: m, value: m, disabled: !isNumeric };
  });
});

const fileStartTime = computed(() => metadata.value?.timeRange?.startTime);
const fileEndTime = computed(() => metadata.value?.timeRange?.endTime);

function disabledDate(current: Date) {
  if (!current) return false;
  const ts = dayjs(current).valueOf();
  if (fileStartTime.value != null && ts < dayjs(fileStartTime.value).subtract(1, 'day').startOf('day').valueOf()) return true;
  if (fileEndTime.value != null && ts > dayjs(fileEndTime.value).add(1, 'day').endOf('day').valueOf()) return true;
  return false;
}

async function loadMetadata() {
  if (!props.fileId) return;
  loading.value = true;
  error.value = null;
  try {
    const response = await metaApi.getMetadata(props.fileId);
    metadata.value = response as TsFileMetadata;
    if (deviceOptions.value.length > 0) {
      selectedDevice.value = deviceOptions.value[0]!.value;
    }
    // In chart mode, default to the first few numeric fields and trigger query
    if (props.chartMode && metadata.value?.measurements) {
      selectedMeasurements.value = defaultChartMeasurements();
      applyFilters();
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : t("tsfile.common.error");
  } finally {
    loading.value = false;
  }
}

function selectQuickTimeRange(range: string) {
  selectedQuickRange.value = range;
  const now = Date.now();
  const map: Record<string, number> = {
    "1h": 3600000,
    "6h": 21600000,
    "24h": 86400000,
    "7d": 604800000,
    "30d": 2592000000,
  };
  const ms = map[range];
  if (ms) {
    timeRange.value = [new Date(now - ms), new Date(now)];
  }
}

function applyFilters() {
  const filters: Record<string, unknown> = {};
  if (selectedDevice.value) filters.devices = [selectedDevice.value];
  // In chart mode, default to the first few numeric fields if none selected
  if (props.chartMode && selectedMeasurements.value.length === 0 && metadata.value?.measurements) {
    selectedMeasurements.value = defaultChartMeasurements();
  }
  if (selectedMeasurements.value.length > 0) filters.measurements = selectedMeasurements.value;
  if (timeRange.value) {
    filters.startTime = dayjs(timeRange.value[0]).valueOf();
    filters.endTime = dayjs(timeRange.value[1]).valueOf();
  }
  if (advancedConditions.value.length > 0) filters.advancedConditions = advancedConditions.value;
  emit("change", filters);
}

function resetFilters() {
  selectedMeasurements.value = [];
  timeRange.value = null;
  selectedQuickRange.value = null;
  advancedConditions.value = [];
  if (deviceOptions.value.length > 0) {
    selectedDevice.value = deviceOptions.value[0]!.value;
  }
  applyFilters();
}

watch(
  () => props.fileId,
  (newId) => {
    if (newId) loadMetadata();
  },
  { immediate: true },
);
watch(
  () => selectedDevice.value,
  () => {
    selectedMeasurements.value = [];
  },
);
watch(timeRange, () => {
  selectedQuickRange.value = null;
});

function handleAdvancedApply(conditions: AdvancedCondition[]) {
  advancedConditions.value = conditions;
}
</script>

<template>
  <div class="tc-panel overflow-hidden">
    <!-- 加载指示条（细线脉冲动效，替代 v-loading 的遮罩 DOM 翻页，避免卡顿） -->
    <div
      class="filter-loading-bar"
      :class="{ active: loading }"
    />
    <div v-if="error" class="px-4 py-4 text-center text-danger">{{ error }}</div>
    <div v-show="!error" class="space-y-4 p-4">
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm text-text-body">{{ t("tsfile.metadata.device") }}:</span>
          <ElSelectV2
            v-model="selectedDevice"
            :options="deviceOptions"
            filterable
            :placeholder="t('tsfile.metadata.searchByDevice')"
            :loading="loading"
            style="width: 240px"
            popper-class="v2-options-compact"
          />
        </div>
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm text-text-body">{{ t("tsfile.data.measurements") }}:</span>
          <ElSelectV2
            v-model="selectedMeasurements"
            :options="measurementSelectOptions"
            multiple
            filterable
            collapse-tags
            :max-collapse-tags="1"
            :placeholder="t('tsfile.data.selectMeasurementsPlaceholder')"
            :disabled="loading || !selectedDevice"
            :loading="loading"
            style="min-width: 220px; max-width: 360px"
            popper-class="v2-options-compact"
          />
        </div>
      </div>
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <el-button
            v-for="range in quickTimeRanges"
            :key="range.value"
            size="small"
            :disabled="loading"
            :type="selectedQuickRange === range.value ? 'primary' : 'default'"
            @click="selectQuickTimeRange(range.value)"
            >{{ range.label }}</el-button
          >
        </div>
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          format="YYYY-MM-DD HH:mm:ss"
          :disabled="loading"
          :disabled-date="disabledDate"
          style="width: 392px"
        />
        <div class="ml-auto flex items-center gap-2">
          <el-button :disabled="loading" @click="showAdvancedDialog = true">
            {{ t("tsfile.data.advancedFilter") }}
            <el-tag v-if="advancedConditions.length > 0" type="primary" class="ml-1">{{
              advancedConditions.length
            }}</el-tag>
          </el-button>
          <el-button type="primary" :disabled="loading" @click="applyFilters">{{ t("tsfile.data.applyFilters") }}</el-button>
          <el-button :disabled="loading" @click="resetFilters">{{ t("tsfile.common.reset") }}</el-button>
        </div>
      </div>
      <div
        v-if="advancedConditions.length > 0"
        class="flex flex-wrap items-center gap-2 rounded-lg bg-bg-subtle p-2"
      >
        <span class="text-sm text-text-label">{{ t("tsfile.data.activeConditions") }}:</span>
        <el-tag
          v-for="(condition, index) in advancedConditions"
          :key="condition.id"
          type="info"
          closable
          @close="advancedConditions.splice(index, 1)"
        >
          {{ condition.field }} {{ condition.operator }} {{ condition.value }}
          <span v-if="index < advancedConditions.length - 1" class="ml-1">{{
            condition.logic
          }}</span>
        </el-tag>
      </div>
    </div>
    <AdvancedFilterDialog
      :open="showAdvancedDialog"
      :conditions="advancedConditions"
      :available-fields="measurementOptions"
      @apply="handleAdvancedApply"
      @update:open="showAdvancedDialog = $event"
    />
  </div>
</template>

<style scoped>
.filter-loading-bar {
  height: 2px;
  width: 100%;
  background: transparent;
  transition: background 0.2s;
  position: relative;
  overflow: hidden;
}

.filter-loading-bar.active {
  background: color-mix(in srgb, var(--primary) 15%, transparent);
}

.filter-loading-bar.active::after {
  content: '';
  position: absolute;
  inset: 0;
  width: 40%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--primary) 50%,
    transparent 100%
  );
  animation: filter-bar-slide 1.4s ease-in-out infinite;
}

@keyframes filter-bar-slide {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(350%); }
}
</style>
