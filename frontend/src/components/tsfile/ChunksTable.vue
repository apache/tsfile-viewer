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
 * ChunksTable 组件 - Chunk 明细表格
 *
 * 与 RowGroupsTable 同构：明细走 /meta/{fileId}/chunks 分页，
 * el-table-v2 虚拟行渲染已加载的页，滚动到底部自动追加下一页。
 *
 * 按测点/设备的搜索框只过滤“已加载”的行，标题里的计数如实反映这一点。
 */
import type { Chunk } from "@/api/tsfile/types";
import type { Column } from "element-plus";
import type { VNode } from "vue";

import { computed, h, ref, watch } from "vue";
import { useI18n } from "vue-i18n";

import { ElAutoResizer, ElTableV2 } from "element-plus";
// 必须显式引入组件样式：unplugin-vue-components 的 ElementPlusResolver 只给
// 模板里出现、由它注入的组件带样式；这里的 ElTableV2/ElAutoResizer 是脚本里
// 手动 import 的，解析器不会补 style，缺了这份 CSS 表格会退化成静态块
// （表头跑到数据下面、行不裁剪互相重叠、内容溢出容器）。
import "element-plus/es/components/table-v2/style/index";
import { Check, ChevronDown, Loader, Search } from "lucide-vue-next";

import { metaApi } from "@/api/tsfile";
import { usePagedList } from "@/composables/usePagedList";
import { formatTimestamp } from "@/utils/timestamp";

interface Props {
  fileId: string;
  scrollY?: number;
}

const props = withDefaults(defineProps<Props>(), {
  scrollY: 400,
});

const { t } = useI18n();

const PAGE_SIZE = 50;
const ROW_HEIGHT = 32;
const STATUS_BAR_HEIGHT = 26;
const MIN_TABLE_HEIGHT = 160;

interface ChunkRow {
  key: string;
  measurement: string;
  device: string;
  startTime: number;
  endTime: number;
  numOfPoints: number;
  offset: number;
  size: number;
  compressionRatio: number;
  dataType: string;
  encoding: string;
  compression: string;
  minValue: string;
  maxValue: string;
}

const { items, total, hasMore, loading, error, loadMore, reset, retry } = usePagedList<Chunk>(
  (offset, limit) => metaApi.getChunksPage(props.fileId, offset, limit),
  { pageSize: PAGE_SIZE },
);

const searchQuery = ref("");

function formatRatio(ratio: number): string {
  return Number.isFinite(ratio) ? ratio.toFixed(2) : "-";
}

function formatTimeRange(startTime?: number, endTime?: number): string {
  if (startTime === undefined && endTime === undefined) return "-";
  return `${formatTimestamp(startTime ?? Number.NaN)} ~ ${formatTimestamp(endTime ?? Number.NaN)}`;
}

// 列宽不足时 el-table-v2 会把单元格截断成省略号，title 保证完整值仍可读
function renderText(value: number | string | undefined): VNode {
  const text = value === undefined || value === "" ? "-" : String(value);
  return h("span", { class: "max-w-full truncate", title: text }, text);
}

// key 用已加载序列里的绝对下标：Chunk 没有天然唯一字段，不能当 row-key
const rows = computed<ChunkRow[]>(() =>
  items.value.map((chunk, position) => ({
    key: `chunk-${position}`,
    measurement: chunk.measurement ?? "",
    device: chunk.device ?? "",
    startTime: chunk.startTime ?? Number.NaN,
    endTime: chunk.endTime ?? Number.NaN,
    numOfPoints: chunk.numOfPoints ?? Number.NaN,
    offset: chunk.offset,
    size: chunk.size,
    compressionRatio: chunk.compressionRatio,
    dataType: chunk.dataType ?? "",
    encoding: chunk.encoding ?? "",
    compression: chunk.compression ?? "",
    minValue: chunk.minValue ?? "",
    maxValue: chunk.maxValue ?? "",
  })),
);

// 只过滤已加载的行：分母是已加载条数而不是 total
const filteredRows = computed<ChunkRow[]>(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) return rows.value;
  return rows.value.filter(
    (row) =>
      row.measurement.toLowerCase().includes(query) || row.device.toLowerCase().includes(query),
  );
});

const countHint = computed(
  () => `${t("tsfile.metadata.showing")} ${rows.value.length} / ${total.value}`,
);
const countLabel = computed(() =>
  searchQuery.value.trim()
    ? `${filteredRows.value.length} / ${rows.value.length}`
    : `${rows.value.length} / ${total.value}`,
);

const tableHeight = computed(() => Math.max(MIN_TABLE_HEIGHT, props.scrollY - STATUS_BAR_HEIGHT));

const columns = computed<Column<ChunkRow>[]>(() => [
  {
    key: "measurement",
    dataKey: "measurement",
    title: t("tsfile.metadata.chunkMeasurement"),
    width: 180,
    flexGrow: 1,
    minWidth: 160,
    cellRenderer: ({ cellData }) =>
      h("span", { class: "block truncate", title: String(cellData ?? "") }, String(cellData || "-")),
  },
  {
    key: "device",
    dataKey: "device",
    title: t("tsfile.metadata.device"),
    width: 120,
    cellRenderer: ({ cellData }) =>
      h("span", { class: "block truncate", title: String(cellData ?? "") }, String(cellData || "-")),
  },
  {
    key: "timeRange",
    title: t("tsfile.metadata.chunkTimeRange"),
    // 12 列挤在 1194px 容器里时 el-table-v2 会按声明宽度等比压缩，
    // 声明宽度给足（380）才能拿到最大份额；压缩后仍单行省略，
    // 绝不换行——换行会被 32px 行高裁掉，看起来像压到下一行。
    width: 380,
    cellRenderer: ({ rowData }) => {
      const text = formatTimeRange(rowData.startTime, rowData.endTime);
      return h("span", { class: "max-w-full truncate font-mono text-xs", title: text }, text);
    },
  },
  {
    key: "numOfPoints",
    title: t("tsfile.metadata.numOfPoints"),
    width: 110,
    // 声明宽度给到 110，压缩后表头「数据点数」仍能排在一行
    align: "center",
    cellRenderer: ({ rowData }) =>
      h("span", Number.isNaN(rowData.numOfPoints) ? "-" : String(rowData.numOfPoints)),
  },
  {
    key: "offset",
    title: t("tsfile.metadata.offset"),
    width: 110,
    cellRenderer: ({ rowData }) => renderText(rowData.offset),
  },
  {
    key: "size",
    title: t("tsfile.metadata.size"),
    width: 80,
    cellRenderer: ({ rowData }) => renderText(rowData.size),
  },
  {
    key: "compressionRatio",
    title: t("tsfile.metadata.compressionRatio"),
    width: 100,
    align: "center",
    cellRenderer: ({ rowData }) => h("span", formatRatio(rowData.compressionRatio)),
  },
  {
    key: "dataType",
    title: t("tsfile.metadata.dataType"),
    width: 100,
    cellRenderer: ({ rowData }) => renderText(rowData.dataType),
  },
  {
    key: "encoding",
    title: t("tsfile.metadata.encoding"),
    width: 110,
    cellRenderer: ({ rowData }) => renderText(rowData.encoding),
  },
  {
    key: "compression",
    title: t("tsfile.metadata.compression"),
    width: 105,
    cellRenderer: ({ rowData }) => renderText(rowData.compression),
  },
  {
    key: "minValue",
    title: t("tsfile.data.minValue"),
    width: 110,
    cellRenderer: ({ rowData }) => renderText(rowData.minValue),
  },
  {
    key: "maxValue",
    title: t("tsfile.data.maxValue"),
    width: 110,
    cellRenderer: ({ rowData }) => renderText(rowData.maxValue),
  },
]);

// 切换文件：丢弃旧的已加载数据，从 offset 0 重新开始
watch(
  () => props.fileId,
  (fileId) => {
    if (fileId) reset();
  },
  { immediate: true },
);

function handleEndReached(): void {
  void loadMore();
}
</script>

<template>
  <div class="tc-panel">
    <div class="tc-panel-title">
      <span>
        {{ t("tsfile.metadata.chunks") }}
        <span class="ml-2 text-xs text-text-body tnum" :title="countHint">
          ({{ countLabel }})
        </span>
      </span>
      <el-input
        v-model="searchQuery"
        :placeholder="t('tsfile.metadata.searchByMeasurement')"
        clearable
        class="w-64"
        size="small"
      >
        <template #prefix>
          <Search class="h-3.5 w-3.5" :stroke-width="1.75" />
        </template>
      </el-input>
    </div>

    <div class="px-4 pb-2 pt-2">
      <div class="tc-table-card" :style="{ height: `${tableHeight}px` }">
        <ElAutoResizer>
          <template #default="{ height, width }">
            <ElTableV2
              :columns="columns"
              :data="filteredRows"
              :width="width"
              :height="height"
              :row-height="ROW_HEIGHT"
              row-key="key"
              :on-end-reached="handleEndReached"
            >
              <template #empty>
                <div class="p-6 text-center text-sm text-text-body">
                  {{ searchQuery ? t("tsfile.common.noData") : t("tsfile.metadata.noChunks") }}
                </div>
              </template>
            </ElTableV2>
          </template>
        </ElAutoResizer>
      </div>

      <div
        class="flex items-center gap-1.5 text-xs text-text-body tnum"
        :style="{ height: `${STATUS_BAR_HEIGHT}px` }"
      >
        <template v-if="error">
          <span class="text-danger">{{ error }}</span>
          <el-button link size="small" type="danger" @click="retry">
            {{ t("tsfile.common.refresh") }}
          </el-button>
        </template>
        <template v-else-if="loading">
          <Loader class="h-3 w-3 animate-spin" :stroke-width="2" />
          {{ t("tsfile.common.loading") }}
        </template>
        <!-- 可点击：本地搜索把已加载行全部过滤掉时表格为空、滚动无法触发加载，
             这里留一个手动继续的入口 -->
        <template v-else-if="hasMore">
          <el-button link size="small" @click="loadMore">
            <ChevronDown class="h-3.5 w-3.5" :stroke-width="2" />
            {{ t("tsfile.data.moreAvailable") }}
          </el-button>
        </template>
        <template v-else>
          <Check class="h-3.5 w-3.5" :stroke-width="2" />
          {{ t("tsfile.metadata.showing") }} {{ rows.length }} / {{ total }}
        </template>
      </div>
    </div>
  </div>
</template>
