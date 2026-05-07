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
 * TableFilterPanel 组件 - 表模型筛选面板
 * 支持表选择、设备多选、字段多选、时间范围筛选、高级条件筛选
 */
import type { AdvancedCondition, TsFileMetadata } from "@/api/tsfile/types";

import { computed, ref, watch } from "vue";

import { Button, DatePicker, Select, Spin, Tag } from "antdv-next";
import { FilterOutlined, ReloadOutlined, SearchOutlined } from "@antdv-next/icons";
import dayjs from "dayjs";
import { useI18n } from "vue-i18n";

import { metaApi } from "@/api/tsfile";
import AdvancedFilterDialog from "@/components/tsfile/AdvancedFilterDialog.vue";

const RangePicker = DatePicker.RangePicker;

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
const error = ref<null | string>(null);
const metadata = ref<null | TsFileMetadata>(null);

// 选择状态
const selectedTable = ref<string | undefined>(undefined);
const selectedDevices = ref<string[]>([]);
const selectedMeasurements = ref<string[]>([]);
// Use `any` for RangePicker value type to avoid dayjs v1/v2 type conflict
// (antdv-next internally uses dayjs v1, project uses dayjs v2)
const timeRange = ref<any>(null);

// 快捷时间范围
const quickTimeRanges = computed(() => [
  { label: t("tsfile.data.last1h"), value: "1h" },
  { label: t("tsfile.data.last6h"), value: "6h" },
  { label: t("tsfile.data.last24h"), value: "24h" },
  { label: t("tsfile.data.last7d"), value: "7d" },
  { label: t("tsfile.data.last30d"), value: "30d" },
]);
const selectedQuickRange = ref<null | string>(null);

// 高级筛选状态
const showAdvancedDialog = ref(false);
const advancedConditions = ref<AdvancedCondition[]>([]);

// 表选项
const tableOptions = computed(() => {
  if (!metadata.value?.tables) return [];
  return metadata.value.tables.map((table) => ({
    label: table.tableName,
    value: table.tableName,
  }));
});

// 设备选项
const deviceOptions = computed(() => {
  if (!metadata.value?.rowGroups) return [];
  const devices = new Set<string>();
  for (const rowGroup of metadata.value.rowGroups) {
    if (rowGroup.device) {
      devices.add(rowGroup.device);
    }
  }
  return [...devices].map((d) => ({ label: d, value: d }));
});

// 字段选项（根据选中的表过滤）
const measurementOptions = computed(() => {
  if (!metadata.value?.tables || !selectedTable.value) return [];
  const table = metadata.value.tables.find((t) => t.tableName === selectedTable.value);
  if (!table) return [];
  return table.fieldColumns.map((col) => col.name).filter(Boolean);
});

const numericTypes = new Set(['INT32', 'INT64', 'FLOAT', 'DOUBLE', 'BOOLEAN']);

const measurementSelectOptions = computed(() => {
  if (!props.chartMode) {
    return measurementOptions.value.map((m) => ({ label: m, value: m }));
  }
  // In chart mode, disable non-numeric fields
  const table = metadata.value?.tables?.find((t) => t.tableName === selectedTable.value);
  const fieldCols = table?.fieldColumns || [];
  return measurementOptions.value.map((m) => {
    const col = fieldCols.find((c) => c.name === m);
    const isNumeric = col ? numericTypes.has(col.dataType?.toUpperCase() || '') : true;
    return { label: m, value: m, disabled: !isNumeric };
  });
});

// 加载元数据
async function loadMetadata() {
  if (!props.fileId) return;

  loading.value = true;
  error.value = null;

  try {
    const response = await metaApi.getMetadata(props.fileId);
    metadata.value = response as TsFileMetadata;

    // 自动选择第一个表
    if (tableOptions.value.length > 0) {
      const firstTable = tableOptions.value[0];
      if (firstTable) {
        selectedTable.value = firstTable.value;
        // In chart mode, auto-select all numeric fields and trigger query
        if (props.chartMode) {
          const table = metadata.value?.tables?.find((t) => t.tableName === firstTable.value);
          if (table) {
            selectedMeasurements.value = table.fieldColumns
              .filter((c) => c.name && numericTypes.has(c.dataType?.toUpperCase() || ''))
              .map((c) => c.name);
            applyFilters();
          }
        }
      }
    }
  } catch (error_: unknown) {
    error.value = error_ instanceof Error ? error_.message : t("tsfile.common.error");
  } finally {
    loading.value = false;
  }
}

const fileStartTime = computed(() => metadata.value?.timeRange?.startTime);
const fileEndTime = computed(() => metadata.value?.timeRange?.endTime);

function disabledDate(current: any) {
  if (!current) return false;
  const ts = current.valueOf();
  if (fileStartTime.value != null && ts < dayjs(fileStartTime.value).subtract(1, 'day').startOf('day').valueOf()) return true;
  if (fileEndTime.value != null && ts > dayjs(fileEndTime.value).add(1, 'day').endOf('day').valueOf()) return true;
  return false;
}

// 选择快捷时间范围
function selectQuickTimeRange(range: string) {
  selectedQuickRange.value = range;
  const now = dayjs();
  let startTime: ReturnType<typeof dayjs>;

  switch (range) {
    case "1h": {
      startTime = now.subtract(1, "hour");
      break;
    }
    case "6h": {
      startTime = now.subtract(6, "hour");
      break;
    }
    case "24h": {
      startTime = now.subtract(24, "hour");
      break;
    }
    case "7d": {
      startTime = now.subtract(7, "day");
      break;
    }
    case "30d": {
      startTime = now.subtract(30, "day");
      break;
    }
    default: {
      return;
    }
  }

  timeRange.value = [startTime, now];
}

// 应用筛选
function applyFilters() {
  const filters: Record<string, unknown> = {};

  if (selectedTable.value) {
    filters.tableName = selectedTable.value;
  }

  if (selectedDevices.value.length > 0) {
    filters.devices = selectedDevices.value;
  }

  // In chart mode, auto-select all numeric fields if none selected
  if (props.chartMode && selectedMeasurements.value.length === 0 && selectedTable.value) {
    const table = metadata.value?.tables?.find((t) => t.tableName === selectedTable.value);
    if (table) {
      selectedMeasurements.value = table.fieldColumns
        .filter((c) => c.name && numericTypes.has(c.dataType?.toUpperCase() || ''))
        .map((c) => c.name);
    }
  }

  if (selectedMeasurements.value.length > 0) {
    filters.measurements = selectedMeasurements.value;
  }

  if (timeRange.value && timeRange.value[0] && timeRange.value[1]) {
    filters.startTime = timeRange.value[0].valueOf();
    filters.endTime = timeRange.value[1].valueOf();
  }

  if (advancedConditions.value.length > 0) {
    filters.advancedConditions = advancedConditions.value;
  }

  emit("change", filters);
}

// 重置筛选
function resetFilters() {
  selectedDevices.value = [];
  selectedMeasurements.value = [];
  timeRange.value = null;
  selectedQuickRange.value = null;
  advancedConditions.value = [];

  // 重置为第一个表
  if (tableOptions.value.length > 0) {
    const firstTable = tableOptions.value[0];
    if (firstTable) {
      selectedTable.value = firstTable.value;
    }
  }

  applyFilters();
}

// 监听 fileId 变化
watch(
  () => props.fileId,
  (newFileId) => {
    if (newFileId) {
      loadMetadata();
    }
  },
  { immediate: true },
);

// 监听表选择变化，重置设备和字段
watch(selectedTable, () => {
  selectedDevices.value = [];
  selectedMeasurements.value = [];
});

// 监听时间范围手动变化，清除快捷选择
watch(timeRange, () => {
  selectedQuickRange.value = null;
});

// 处理高级筛选应用
function handleAdvancedApply(conditions: AdvancedCondition[]) {
  advancedConditions.value = conditions;
}
</script>

<template>
  <div
    class="table-filter-panel rounded-lg border border-solid border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-900"
  >
    <!-- 加载状态 -->
    <div v-if="loading" class="flex items-center justify-center py-4">
      <Spin />
      <span class="ml-2 text-sm text-gray-500">{{ t("tsfile.common.loading") }}</span>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="text-center text-red-500">
      {{ error }}
    </div>

    <!-- 筛选表单 -->
    <div v-else class="space-y-4">
      <!-- 第一行：表/设备/字段选择 -->
      <div class="flex flex-wrap items-center gap-4">
        <!-- 表选择 -->
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm"> {{ t("tsfile.data.selectTable") }}: </span>
          <Select
            v-model:value="selectedTable"
            :placeholder="t('tsfile.data.selectTablePlaceholder')"
            :options="tableOptions"
            show-search
            style="width: 192px"
          />
        </div>

        <!-- 设备多选 -->
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm"> {{ t("tsfile.data.devices") }}: </span>
          <Select
            v-model:value="selectedDevices"
            mode="multiple"
            :placeholder="t('tsfile.data.selectDevicesPlaceholder')"
            :options="deviceOptions"
            :disabled="!selectedTable"
            show-search
            :max-tag-count="1"
            style="min-width: 220px; max-width: 360px"
          />
        </div>

        <!-- 字段多选 -->
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm"> {{ t("tsfile.data.fields") }}: </span>
          <Select
            v-model:value="selectedMeasurements"
            mode="multiple"
            :placeholder="t('tsfile.data.selectFieldsPlaceholder')"
            :options="measurementSelectOptions"
            :disabled="!selectedTable"
            show-search
            :max-tag-count="1"
            style="min-width: 192px; max-width: 360px"
          />
        </div>
      </div>

      <!-- 第二行：时间范围 -->
      <div class="flex flex-wrap items-center gap-4">
        <!-- 快捷时间范围 -->
        <div class="flex items-center gap-2">
          <Button
            v-for="range in quickTimeRanges"
            :key="range.value"
            size="small"
            :type="selectedQuickRange === range.value ? 'primary' : 'default'"
            @click="selectQuickTimeRange(range.value)"
          >
            {{ range.label }}
          </Button>
        </div>

        <!-- 自定义时间范围 -->
        <div class="flex items-center gap-2">
          <RangePicker
            v-model:value="timeRange"
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            :placeholder="[t('tsfile.metadata.startTime'), t('tsfile.metadata.endTime')]"
            :disabled-date="disabledDate"
          />
        </div>

        <!-- 操作按钮 -->
        <div class="ml-auto flex items-center gap-2">
          <Button @click="showAdvancedDialog = true">
            <template #icon>
              <FilterOutlined />
            </template>
            {{ t("tsfile.data.advancedFilter") }}
            <Tag v-if="advancedConditions.length > 0" color="blue" class="ml-1">
              {{ advancedConditions.length }}
            </Tag>
          </Button>
          <Button type="primary" @click="applyFilters">
            <template #icon>
              <SearchOutlined />
            </template>
            {{ t("tsfile.data.applyFilters") }}
          </Button>
          <Button @click="resetFilters">
            <template #icon>
              <ReloadOutlined />
            </template>
            {{ t("tsfile.common.reset") }}
          </Button>
        </div>
      </div>

      <!-- 高级条件摘要 -->
      <div
        v-if="advancedConditions.length > 0"
        class="flex flex-wrap items-center gap-2 rounded-lg bg-blue-50 p-2 dark:bg-blue-900/20"
      >
        <span class="text-sm text-gray-600 dark:text-gray-400">
          {{ t("tsfile.data.activeConditions") }}:
        </span>
        <Tag
          v-for="(condition, index) in advancedConditions"
          :key="condition.id"
          closable
          @close="advancedConditions.splice(index, 1)"
        >
          {{ condition.field }} {{ condition.operator }} {{ condition.value }}
          <span v-if="index < advancedConditions.length - 1" class="ml-1">
            {{ condition.logic }}
          </span>
        </Tag>
      </div>
    </div>

    <!-- 高级筛选对话框 -->
    <AdvancedFilterDialog
      :open="showAdvancedDialog"
      :conditions="advancedConditions"
      :available-fields="measurementOptions"
      @apply="handleAdvancedApply"
      @update:open="showAdvancedDialog = $event"
    />
  </div>
</template>
