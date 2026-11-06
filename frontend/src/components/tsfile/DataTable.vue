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
 * DataTable 组件 - 数据表格
 * 支持动态列、分页、排序、导出
 *
 * 渲染策略：Element Plus 的 el-table 没有虚拟滚动，因此这里**依赖既有的
 * “列分页”机制**把同时渲染的字段列压到 columnPageSize（默认 50，上限 200）
 * 以内；行数由后端 limit 控制（默认 100，上限 1000）。列 × 行的单元格量级
 * 在这个范围内普通 el-table 可以承受，不需要 el-table-v2。
 */
import type { DataRow } from "@/api/tsfile/types";

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";

import { ChevronLeft, ChevronRight, Download } from "lucide-vue-next";

import { formatTimestamp } from "@/utils/timestamp";
import { tableStyleProps } from "@/utils/tableStyle";

interface Props {
  data: DataRow[];
  offset: number;
  limit?: number;
  hasMore: boolean;
  loading: boolean;
  error: string | null;
  tagColumns?: string[];
  /** 导出进行中：取全量数据期间禁用导出按钮 */
  exporting?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  limit: 100,
  tagColumns: () => [],
  exporting: false,
});

const emit = defineEmits<{
  export: [format: "csv" | "json"];
  limitChange: [limit: number];
  pageChange: [page: number];
}>();

const { t } = useI18n();

const internalLimit = ref(props.limit);
const sortColumn = ref<string | null>(null);
const sortDirection = ref<"asc" | "desc">("asc");

// 字段列分页（避免一次性渲染数千列导致页面卡死）
const columnPage = ref(1);
const columnPageSize = ref(50);
const columnSearch = ref("");

const limitOptions = [10, 20, 50, 100, 200, 500, 1000];

const columnPageSizeOptions = [20, 50, 100, 200];

// 行分页是游标式的：预览接口只多读一行来探测"是否还有下一页"，拿不到数据集
// 总行数（见后端 DataPreviewResponse.total 的说明），所以这里只按
// offset / limit / hasMore 推导，不显示总页数。
const currentPage = computed(() => Math.floor(props.offset / internalLimit.value) + 1);

// 当前页的行区间；空页时归零，避免出现 "第 101–100 行"
const rowRangeStart = computed(() => (props.data.length === 0 ? 0 : props.offset + 1));
const rowRangeEnd = computed(() => props.offset + props.data.length);

// 翻到最后一页后（没有更多数据）区间末尾就是过滤后的真实总行数，此时才敢报总数
const knownTotalRows = computed(() => (props.hasMore ? null : rowRangeEnd.value));

const canGoPrev = computed(() => props.offset > 0);
const canGoNext = computed(() => props.hasMore);

// 获取所有测点列
const measurementColumns = computed(() => {
  const columns = new Set<string>();
  for (const row of props.data) {
    for (const key of Object.keys(row.measurements)) {
      columns.add(key);
    }
  }
  return [...columns].toSorted();
});

// 字段列（非标签列）—— 全量
const fieldColumnNames = computed(() => {
  const tagSet = new Set(props.tagColumns);
  return measurementColumns.value.filter((col) => !tagSet.has(col));
});

// 按搜索关键字过滤字段列
const filteredFieldColumns = computed(() => {
  const q = columnSearch.value.trim().toLowerCase();
  if (!q) return fieldColumnNames.value;
  return fieldColumnNames.value.filter((col) => col.toLowerCase().includes(q));
});

// 字段列总页数
const fieldColumnTotalPages = computed(() =>
  Math.max(1, Math.ceil(filteredFieldColumns.value.length / columnPageSize.value)),
);

// 当前页可见的字段列
const visibleFieldColumns = computed(() => {
  const start = (columnPage.value - 1) * columnPageSize.value;
  return filteredFieldColumns.value.slice(start, start + columnPageSize.value);
});

// 当前可见字段列区间（用于展示 "X–Y / 总数"）
const fieldColumnRange = computed(() => {
  const totalCols = filteredFieldColumns.value.length;
  if (totalCols === 0) return { start: 0, end: 0, total: 0 };
  const start = (columnPage.value - 1) * columnPageSize.value;
  return {
    start: start + 1,
    end: Math.min(start + columnPageSize.value, totalCols),
    total: totalCols,
  };
});

// 当列搜索 / 每页列数 / 数据变化时，重置到第一页并防止页码越界
watch([columnSearch, columnPageSize, fieldColumnNames], () => {
  columnPage.value = 1;
});
watch(fieldColumnTotalPages, (pages) => {
  if (columnPage.value > pages) columnPage.value = pages;
});

// 表格纵向可视高度：用 ResizeObserver 实测表格外层容器的可用高度，
// 避免用写死常量手算上方元素（列控制条 / 精度说明 Alert / 分页）的高度——
// 上方任意增删元素时，表格高度都能自适应，不再错位。
//
// 与旧组件库的差异：旧组件库的 `scroll.y` 指的是**表体**高度，需要额外减掉
// 表头；el-table 的 `height` 是**含表头**的整体高度，因此不再减表头，
// 只保留一点边框/横向滚动条的余量。表头实测仅用于兜出一个合理的下限
// （表头 + 至少一行），像素常量已按此重新校准。
const tableWrapper = ref<HTMLElement | null>(null);
const tableHeight = ref(400);
let resizeObserver: ResizeObserver | null = null;
// 表头 DOM 未就绪时的兜底表头高度
const HEADER_HEIGHT_FALLBACK = 40;
// 横向滚动条 + 底部边框预留，避免表格压到下方分页
const SCROLLBAR_RESERVE = 12;
// 下限至少放得下表头 + 一行数据
const MIN_ROW_HEIGHT = 36;

function measureTableHeight() {
  const el = tableWrapper.value;
  if (!el) return;
  // 实测真实表头高度（适配 size / 多行表头 / 字体变化），拿不到则用兜底值
  const headerEl = el.querySelector<HTMLElement>(".el-table__header-wrapper");
  const headerHeight = headerEl?.offsetHeight || HEADER_HEIGHT_FALLBACK;
  const available = el.clientHeight - SCROLLBAR_RESERVE;
  tableHeight.value = Math.max(headerHeight + MIN_ROW_HEIGHT, Math.floor(available));
}

onMounted(() => {
  measureTableHeight();
  if (typeof ResizeObserver !== "undefined" && tableWrapper.value) {
    resizeObserver = new ResizeObserver(() => measureTableHeight());
    resizeObserver.observe(tableWrapper.value);
  }
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
});

// loading 结束 / 数据变化后，表头与上下方控制条、分页的显隐会改变，
// 等 DOM 更新后重新实测高度，确保表格不覆盖分页。
watch(
  () => [props.loading, props.data.length, props.offset] as const,
  () => nextTick(measureTableHeight),
);

// 表格数据（扁平化）—— 仅展开当前可见列，避免每行生成数千个属性
const tableData = computed(() => {
  const visibleMeasurements = [...props.tagColumns, ...visibleFieldColumns.value];
  let data = props.data.map((row, index) => {
    const flatRow: Record<string, unknown> = {
      _key: `${row.timestamp}-${row.device}-${index}`,
      timestamp: row.timestamp,
      timestampRaw: row.timestampRaw,
      __device__: row.device,
    };
    for (const measurement of visibleMeasurements) {
      flatRow[measurement] = formatValue(row.measurements[measurement]);
    }
    return flatRow;
  });

  // 客户端排序
  if (sortColumn.value) {
    const column = sortColumn.value;
    data = [...data].toSorted((a, b) => {
      const aVal = a[column];
      const bVal = b[column];

      const comparison =
        typeof aVal === "number" && typeof bVal === "number"
          ? aVal - bVal
          : String(aVal).localeCompare(String(bVal));

      return sortDirection.value === "asc" ? comparison : -comparison;
    });
  }

  return data;
});

// 监听 limit prop 变化
watch(
  () => props.limit,
  (newLimit) => {
    if (newLimit && newLimit !== internalLimit.value) {
      internalLimit.value = newLimit;
    }
  },
);

// 处理分页变化
function goToPrevPage() {
  if (canGoPrev.value) emit("pageChange", currentPage.value - 1);
}

function goToNextPage() {
  if (canGoNext.value) emit("pageChange", currentPage.value + 1);
}

// 处理每页条数变化
function handleLimitChange(limit: number) {
  internalLimit.value = limit;
  emit("limitChange", limit);
}

// 处理字段列分页变化
function handleColumnPageChange(page: number) {
  columnPage.value = page;
}

// 处理排序。Element Plus 的方向枚举是 ascending/descending（旧组件库是 ascend/descend），
// 传 null 表示取消排序。
function handleSortChange(payload: {
  prop: string | null;
  order: "ascending" | "descending" | null;
}) {
  if (payload.order && payload.prop) {
    sortColumn.value = payload.prop;
    sortDirection.value = payload.order === "ascending" ? "asc" : "desc";
  } else {
    sortColumn.value = null;
  }
}

// 将不同精度的时间戳归一到毫秒并格式化，见 utils/timestamp.ts
// 格式化值
function formatValue(value: unknown): number | string {
  if (value === null || value === undefined) return "-";
  if (typeof value === "number") return value;
  return String(value);
}
</script>

<template>
  <div class="data-table tc-panel flex h-full flex-col">
    <div class="tc-panel-title">
      <span>{{ t("tsfile.data.title") }}</span>
      <div class="flex gap-2">
        <el-button size="small" :disabled="exporting" :loading="exporting" @click="emit('export', 'csv')">
          <Download class="mr-1 h-3.5 w-3.5" :stroke-width="1.75" />
          {{ t("tsfile.data.exportCsv") }}
        </el-button>
        <el-button size="small" :disabled="exporting" :loading="exporting" @click="emit('export', 'json')">
          <Download class="mr-1 h-3.5 w-3.5" :stroke-width="1.75" />
          {{ t("tsfile.data.exportJson") }}
        </el-button>
      </div>
    </div>

    <div class="flex min-h-0 flex-1 flex-col p-4">
      <!-- 错误提示 -->
      <el-alert
        v-if="error"
        type="error"
        :title="t('tsfile.error.loadFailed')"
        show-icon
        :closable="false"
        class="mb-4"
      >
        {{ error }}
      </el-alert>

      <!-- 字段列控制条：搜索 + 列分页（避免一次性渲染数千列）。整体右对齐，
           与下方表格右侧的横向滚动区对齐，左侧留白让位给表格首列 -->
      <div
        v-if="!error && fieldColumnNames.length > columnPageSize"
        class="mb-3 flex flex-wrap items-center justify-end gap-3"
      >
        <el-input
          v-model="columnSearch"
          :placeholder="t('tsfile.data.searchColumn')"
          clearable
          size="small"
          style="width: 220px"
        />
        <div class="flex items-center gap-2">
          <span class="text-[0.8125rem] text-text-body">
            {{ t("tsfile.data.columnsPerPage") }}:
          </span>
          <el-select v-model="columnPageSize" size="small" style="width: 84px">
            <el-option
              v-for="size in columnPageSizeOptions"
              :key="size"
              :label="String(size)"
              :value="size"
            />
          </el-select>
        </div>
        <span class="text-[0.8125rem] text-text-body tnum">
          {{ t("tsfile.data.columns") }} {{ fieldColumnRange.start }}–{{
            fieldColumnRange.end
          }}
          / {{ fieldColumnRange.total }}
        </span>
        <el-pagination
          :current-page="columnPage"
          :page-size="columnPageSize"
          :total="filteredFieldColumns.length"
          size="small"
          layout="prev, pager, next"
          @current-change="handleColumnPageChange"
        />
      </div>

      <!-- 时间戳精度说明 -->
      <el-alert
        v-if="!error"
        type="info"
        show-icon
        :title="t('tsfile.data.precisionNote')"
        :closable="false"
        class="mb-3"
      />

      <!-- 数据表格 —— 外层 wrapper 撑满剩余空间，实测其高度供 el-table 使用 -->
      <div
        v-if="!error"
        ref="tableWrapper"
        class="tc-table-card min-h-0 flex-1"
      >
        <el-table
          v-bind="tableStyleProps"
          v-loading="loading"
          :data="tableData"
          :height="tableHeight"
          border
          row-key="_key"
          :empty-text="t('tsfile.data.noDataFound')"
          @sort-change="handleSortChange"
        >
          <el-table-column
            :label="t('tsfile.data.timestamp')"
            prop="timestamp"
            fixed="left"
            :width="210"
            sortable="custom"
          >
            <template #default="{ row }">
              <el-tooltip
                :content="`${t('tsfile.data.rawTimestamp')}: ${row.timestampRaw ?? row.timestamp}`"
                placement="top"
              >
                <span class="timestamp-cell font-mono text-xs">
                  {{ formatTimestamp(row.timestamp as number) }}
                </span>
              </el-tooltip>
            </template>
          </el-table-column>

          <el-table-column
            :label="t('tsfile.data.device')"
            prop="__device__"
            fixed="left"
            :width="180"
            sortable="custom"
            show-overflow-tooltip
          />

          <!--
            标签列（固定左侧）—— tag 名可能与保留列同名（如 "device"），
            固定"设备"列已改用保留 key "__device__" 避免冲突，此处 tag 列可安全使用原名。
          -->
          <el-table-column
            v-for="tagCol in tagColumns"
            :key="`tag-${tagCol}`"
            :label="tagCol"
            :prop="tagCol"
            fixed="left"
            :width="120"
            sortable="custom"
            show-overflow-tooltip
          />

          <!-- 字段列（可滚动）—— 仅渲染当前列分页窗口内的列 -->
          <el-table-column
            v-for="fieldCol in visibleFieldColumns"
            :key="`field-${fieldCol}`"
            :label="fieldCol"
            :prop="fieldCol"
            :width="120"
            sortable="custom"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span :class="typeof row[fieldCol] === 'number' ? 'font-mono' : ''">
                {{ row[fieldCol] }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 行分页：游标式（上一页 / 下一页），总行数只在读到末页后才可知 -->
      <div
        v-if="!loading && (data.length > 0 || offset > 0)"
        class="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border-default pt-4"
      >
        <div class="flex flex-wrap items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="text-[0.8125rem] text-text-body">
              {{ t("tsfile.data.limit") }}:
            </span>
            <el-select
              :model-value="internalLimit"
              size="small"
              style="width: 94px"
              @update:model-value="handleLimitChange"
            >
              <el-option
                v-for="option in limitOptions"
                :key="option"
                :label="String(option)"
                :value="option"
              />
            </el-select>
          </div>
          <span class="text-[0.8125rem] text-text-body tnum">
            <template v-if="knownTotalRows !== null">
              {{
                t("tsfile.data.rowRangeTotal", {
                  start: rowRangeStart,
                  end: rowRangeEnd,
                  total: knownTotalRows,
                })
              }}
            </template>
            <template v-else>
              {{ t("tsfile.data.rowRange", { start: rowRangeStart, end: rowRangeEnd }) }}
            </template>
          </span>
        </div>

        <div class="flex items-center gap-2">
          <span class="text-[0.8125rem] text-text-body tnum">
            {{ t("tsfile.data.pageNumber", { page: currentPage }) }}
          </span>
          <el-button size="small" :disabled="!canGoPrev" @click="goToPrevPage">
            <ChevronLeft class="mr-1 h-3.5 w-3.5" :stroke-width="1.75" />
            {{ t("tsfile.data.prevPage") }}
          </el-button>
          <el-button size="small" :disabled="!canGoNext" @click="goToNextPage">
            {{ t("tsfile.data.nextPage") }}
            <ChevronRight class="ml-1 h-3.5 w-3.5" :stroke-width="1.75" />
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 时间戳单元格：虚线下划线提示悬浮可查看原始存储值 */
.timestamp-cell {
  border-bottom: 1px dotted var(--border-default);
  cursor: help;
}
</style>
