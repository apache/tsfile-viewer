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
import type { AdvancedCondition, Table, TsFileMetadata } from "@/api/tsfile/types";

import { computed, ref, watch } from "vue";

import dayjs from "dayjs";
import { useI18n } from "vue-i18n";
import { Filter, RotateCcw, Search } from "lucide-vue-next";

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
const error = ref<null | string>(null);
const metadata = ref<null | TsFileMetadata>(null);

// 选择状态
const selectedTable = ref<string | undefined>(undefined);
const selectedDevices = ref<string[]>([]);
const selectedMeasurements = ref<string[]>([]);
const timeRange = ref<[Date, Date] | null>(null);

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

// 图表模式下默认展示的序列数上限（避免一次性绘制数千条折线导致页面卡死）
const CHART_DEFAULT_SERIES = 8;

// 取某张表前 N 个数值字段，作为图表默认序列
function defaultChartMeasurements(table: Table | undefined): string[] {
  if (!table) return [];
  return table.fieldColumns
    .filter((c) => c.name && numericTypes.has(c.dataType?.toUpperCase() || ''))
    .map((c) => c.name)
    .slice(0, CHART_DEFAULT_SERIES);
}

const measurementSelectOptions = computed(() => {
  if (!props.chartMode) {
    return measurementOptions.value.map((m) => ({ label: m, value: m, disabled: false }));
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
            // 默认仅展示前若干条数值序列，避免一次性绘制数千条折线
            selectedMeasurements.value = defaultChartMeasurements(table);
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

function disabledDate(current: Date) {
  if (!current) return false;
  const ts = dayjs(current).valueOf();
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

  timeRange.value = [startTime.toDate(), now.toDate()];
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

  // In chart mode, default to the first few numeric fields if none selected
  if (props.chartMode && selectedMeasurements.value.length === 0 && selectedTable.value) {
    const table = metadata.value?.tables?.find((t) => t.tableName === selectedTable.value);
    selectedMeasurements.value = defaultChartMeasurements(table);
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
  <div class="tc-panel table-filter-panel p-4">
    <!-- 错误状态 -->
    <div v-if="error" class="py-4 text-center text-danger">
      {{ error }}
    </div>

    <!-- 筛选表单 -->
    <div v-else v-loading="loading" class="space-y-4">
      <!-- 第一行：表/设备/字段选择 -->
      <div class="flex flex-wrap items-center gap-4">
        <!-- 表选择 -->
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm text-text-body"> {{ t("tsfile.data.selectTable") }}: </span>
          <el-select
            v-model="selectedTable"
            :placeholder="t('tsfile.data.selectTablePlaceholder')"
            filterable
            style="width: 192px"
          >
            <el-option v-for="opt in tableOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>

        <!-- 设备多选 -->
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm text-text-body"> {{ t("tsfile.data.devices") }}: </span>
          <el-select
            v-model="selectedDevices"
            multiple
            :placeholder="t('tsfile.data.selectDevicesPlaceholder')"
            :disabled="!selectedTable"
            filterable
            collapse-tags
            :max-collapse-tags="1"
            style="min-width: 220px; max-width: 360px"
          >
            <el-option v-for="opt in deviceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>

        <!-- 字段多选 -->
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap text-sm text-text-body"> {{ t("tsfile.data.fields") }}: </span>
          <el-select
            v-model="selectedMeasurements"
            multiple
            :placeholder="t('tsfile.data.selectFieldsPlaceholder')"
            :disabled="!selectedTable"
            filterable
            collapse-tags
            :max-collapse-tags="1"
            style="min-width: 192px; max-width: 360px"
          >
            <el-option
              v-for="opt in measurementSelectOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
              :disabled="opt.disabled"
            />
          </el-select>
        </div>
      </div>

      <!-- 第二行：时间范围 -->
      <div class="flex flex-wrap items-center gap-4">
        <!-- 快捷时间范围 -->
        <div class="flex items-center gap-2">
          <el-button
            v-for="range in quickTimeRanges"
            :key="range.value"
            size="small"
            :type="selectedQuickRange === range.value ? 'primary' : 'default'"
            @click="selectQuickTimeRange(range.value)"
          >
            {{ range.label }}
          </el-button>
        </div>

        <!-- 自定义时间范围。Element Plus 给 datetimerange 的默认宽度（400px）
             是按更长的日期格式留的；实测两端 "YYYY-MM-DD HH:mm:ss" 加上分隔符
             与清空图标后，372px 仍会把时间文本截掉，因此再放宽 20px -->
        <div class="flex items-center gap-2">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            format="YYYY-MM-DD HH:mm:ss"
            :start-placeholder="t('tsfile.metadata.startTime')"
            :end-placeholder="t('tsfile.metadata.endTime')"
            :disabled-date="disabledDate"
            style="width: 392px"
          />
        </div>

        <!-- 操作按钮 -->
        <div class="ml-auto flex items-center gap-2">
          <el-button @click="showAdvancedDialog = true">
            <Filter :size="14" class="mr-1" />
            {{ t("tsfile.data.advancedFilter") }}
            <el-tag v-if="advancedConditions.length > 0" type="primary" class="ml-1">
              {{ advancedConditions.length }}
            </el-tag>
          </el-button>
          <el-button type="primary" @click="applyFilters">
            <Search :size="14" class="mr-1" />
            {{ t("tsfile.data.applyFilters") }}
          </el-button>
          <el-button @click="resetFilters">
            <RotateCcw :size="14" class="mr-1" />
            {{ t("tsfile.common.reset") }}
          </el-button>
        </div>
      </div>

      <!-- 高级条件摘要 -->
      <div
        v-if="advancedConditions.length > 0"
        class="flex flex-wrap items-center gap-2 rounded-lg bg-bg-subtle p-2"
      >
        <span class="text-sm text-text-label">
          {{ t("tsfile.data.activeConditions") }}:
        </span>
        <el-tag
          v-for="(condition, index) in advancedConditions"
          :key="condition.id"
          type="info"
          closable
          @close="advancedConditions.splice(index, 1)"
        >
          {{ condition.field }} {{ condition.operator }} {{ condition.value }}
          <span v-if="index < advancedConditions.length - 1" class="ml-1">
            {{ condition.logic }}
          </span>
        </el-tag>
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
