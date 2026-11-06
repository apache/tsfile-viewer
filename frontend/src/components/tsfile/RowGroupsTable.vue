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
 * RowGroupsTable 组件 - RowGroup 明细表格
 *
 * 大文件可能有数万个 RowGroup（曾出现过 87,488 个把后端撑到 OOM），
 * 因此明细走 /meta/{fileId}/row-groups 分页：el-table-v2 虚拟行渲染已加载的页，
 * 滚动到底部自动追加下一页，hasMore 为 false 后不再请求。
 *
 * 按 device 的搜索框只过滤“已加载”的行，标题里的计数如实反映这一点。
 */
import type { RowGroup } from "@/api/tsfile/types";
import type { Column } from "element-plus";

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
// 底部状态条：加载中 / 还有更多 / 已到底
const STATUS_BAR_HEIGHT = 26;
const MIN_TABLE_HEIGHT = 160;

interface RowGroupRow {
  key: string;
  index: number;
  device: string;
  startTime: number;
  endTime: number;
  chunkCount: number;
}

const { items, total, hasMore, loading, error, loadMore, reset, retry } = usePagedList<RowGroup>(
  (offset, limit) => metaApi.getRowGroupsPage(props.fileId, offset, limit),
  { pageSize: PAGE_SIZE },
);

const searchQuery = ref("");

// key 用已加载序列里的绝对下标：后端 rowGroup.index 未必跨页唯一，不能拿来当 row-key
const rows = computed<RowGroupRow[]>(() =>
  items.value.map((rowGroup, position) => ({
    key: `rg-${position}`,
    index: rowGroup.index,
    device: rowGroup.device ?? "",
    startTime: rowGroup.startTime,
    endTime: rowGroup.endTime,
    chunkCount: rowGroup.chunkCount,
  })),
);

// 只过滤已加载的行：分母是 loadedCount 而不是 total
const filteredRows = computed<RowGroupRow[]>(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) return rows.value;
  return rows.value.filter((row) => row.device.toLowerCase().includes(query));
});

const countHint = computed(
  () => `${t("tsfile.metadata.showing")} ${rows.value.length} / ${total.value}`,
);
// 搜索中显示“命中的行 / 已加载的行”，否则显示“已加载的行 / 文件总行数”
const countLabel = computed(() =>
  searchQuery.value.trim()
    ? `${filteredRows.value.length} / ${rows.value.length}`
    : `${rows.value.length} / ${total.value}`,
);

const tableHeight = computed(() => Math.max(MIN_TABLE_HEIGHT, props.scrollY - STATUS_BAR_HEIGHT));

const columns = computed<Column<RowGroupRow>[]>(() => [
  {
    key: "index",
    dataKey: "index",
    title: t("tsfile.metadata.rowGroupIndex"),
    width: 90,
    align: "center",
  },
  {
    key: "device",
    dataKey: "device",
    title: t("tsfile.metadata.device"),
    width: 240,
    flexGrow: 1,
    minWidth: 160,
    cellRenderer: ({ cellData }) =>
      h("span", { class: "block truncate", title: String(cellData ?? "") }, String(cellData || "-")),
  },
  {
    key: "timeRange",
    title: t("tsfile.metadata.timeRange"),
    // 38 位时间范围文本在 12px 等宽字体下约 355px，留出单元格左右 padding。
    // minWidth 是列宽的下限（容器变窄时 el-table-v2 会压缩其它列而不是这列）；
    // 仍保留 truncate：再窄就单行省略，不能换行——换行会被 32px 行高裁掉，
    // 视觉上像压到下一行。
    width: 380,
    minWidth: 350,
    cellRenderer: ({ rowData }) => {
      const text = `${formatTimestamp(rowData.startTime)} ~ ${formatTimestamp(rowData.endTime)}`;
      return h("span", { class: "max-w-full truncate font-mono text-xs", title: text }, text);
    },
  },
  {
    key: "chunkCount",
    dataKey: "chunkCount",
    title: t("tsfile.metadata.chunkCount"),
    width: 120,
    align: "center",
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
        {{ t("tsfile.metadata.rowGroups") }}
        <span class="ml-2 text-xs text-text-body tnum" :title="countHint">
          ({{ countLabel }})
        </span>
      </span>
      <el-input
        v-model="searchQuery"
        :placeholder="t('tsfile.metadata.searchByDevice')"
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
                  {{ searchQuery ? t("tsfile.common.noData") : t("tsfile.metadata.noRowGroups") }}
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
