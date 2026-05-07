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
 * 使用 antdv-next 组件
 */
import type { AdvancedCondition, TsFileMetadata } from "@/api/tsfile/types";

import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";

import { Button, Select, Tag } from "antdv-next";
import { DatePicker } from "antdv-next";
import dayjs from "dayjs";

import { metaApi } from "@/api/tsfile";
import AdvancedFilterDialog from "@/components/tsfile/AdvancedFilterDialog.vue";

const { RangePicker } = DatePicker;

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
const timeRange = ref<any>(null);

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

const measurementSelectOptions = computed(() => {
  if (!props.chartMode) {
    return measurementOptions.value.map((m) => ({ label: m, value: m }));
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

function disabledDate(current: any) {
  if (!current) return false;
  const ts = current.valueOf();
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
    // In chart mode, auto-select all numeric fields and trigger query
    if (props.chartMode && metadata.value?.measurements) {
      selectedMeasurements.value = metadata.value.measurements
        .filter((m) => m.name && numericTypes.has(m.dataType?.toUpperCase() || ''))
        .map((m) => m.name);
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
  const now = dayjs();
  const map: Record<string, number> = {
    "1h": 3600000,
    "6h": 21600000,
    "24h": 86400000,
    "7d": 604800000,
    "30d": 2592000000,
  };
  const ms = map[range];
  if (ms) {
    timeRange.value = [dayjs(now.valueOf() - ms), now];
  }
}

function applyFilters() {
  const filters: Record<string, unknown> = {};
  if (selectedDevice.value) filters.devices = [selectedDevice.value];
  // In chart mode, auto-select all numeric fields if none selected
  if (props.chartMode && selectedMeasurements.value.length === 0 && metadata.value?.measurements) {
    selectedMeasurements.value = metadata.value.measurements
      .filter((m) => m.name && numericTypes.has(m.dataType?.toUpperCase() || ''))
      .map((m) => m.name);
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
  <div class="rounded-lg border p-4">
    <div v-if="loading" class="flex items-center justify-center py-4">
      <span class="text-gray-500">{{ t("tsfile.common.loading") }}</span>
    </div>
    <div v-else-if="error" class="text-center text-red-500">{{ error }}</div>
    <div v-else class="space-y-4">
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm">{{ t("tsfile.metadata.device") }}:</span>
          <Select
            v-model:value="selectedDevice"
            show-search
            :placeholder="t('tsfile.metadata.searchByDevice')"
            :options="deviceOptions"
            style="width: 240px"
          />
        </div>
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm">{{ t("tsfile.data.measurements") }}:</span>
          <Select
            v-model:value="selectedMeasurements"
            mode="multiple"
            show-search
            :max-tag-count="1"
            :placeholder="t('tsfile.data.selectMeasurementsPlaceholder')"
            :disabled="!selectedDevice"
            :options="measurementSelectOptions"
            style="min-width: 220px; max-width: 360px"
          />
        </div>
      </div>
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <Button
            v-for="range in quickTimeRanges"
            :key="range.value"
            size="small"
            :type="selectedQuickRange === range.value ? 'primary' : 'default'"
            @click="selectQuickTimeRange(range.value)"
            >{{ range.label }}</Button
          >
        </div>
        <RangePicker v-model:value="timeRange" show-time format="YYYY-MM-DD HH:mm:ss" :disabled-date="disabledDate" />
        <div class="ml-auto flex items-center gap-2">
          <Button @click="showAdvancedDialog = true">
            {{ t("tsfile.data.advancedFilter") }}
            <Tag v-if="advancedConditions.length > 0" color="blue" class="ml-1">{{
              advancedConditions.length
            }}</Tag>
          </Button>
          <Button type="primary" @click="applyFilters">{{ t("tsfile.data.applyFilters") }}</Button>
          <Button @click="resetFilters">{{ t("tsfile.common.reset") }}</Button>
        </div>
      </div>
      <div
        v-if="advancedConditions.length > 0"
        class="flex flex-wrap items-center gap-2 rounded-lg bg-blue-50 p-2"
      >
        <span class="text-sm text-gray-600">{{ t("tsfile.data.activeConditions") }}:</span>
        <Tag
          v-for="(condition, index) in advancedConditions"
          :key="condition.id"
          closable
          @close="advancedConditions.splice(index, 1)"
        >
          {{ condition.field }} {{ condition.operator }} {{ condition.value }}
          <span v-if="index < advancedConditions.length - 1" class="ml-1">{{
            condition.logic
          }}</span>
        </Tag>
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
