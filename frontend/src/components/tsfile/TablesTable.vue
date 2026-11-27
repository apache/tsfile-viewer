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
 * TablesTable 组件 - 表模型的表列表
 * 显示表名、标签列、字段列
 */
import type { Table } from "@/api/tsfile/types";

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";

import { tableStyleProps } from "@/utils/tableStyle";
import { getDataTypeTagType } from "@/utils/dataTypeTag";

interface Props {
  tables: Table[];
  loading?: boolean;
  scrollY?: number;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  scrollY: 400,
});

const { t } = useI18n();

const activeKeys = ref<string[]>([]);

/**
 * 只渲染已展开面板里的两张表。
 *
 * el-collapse-item 用 v-show 隐藏折叠内容，插槽里的 el-table 依然会挂载；表多的文件
 * （例如 225 张表的 tsfile）一开页就要建出几百个 el-table，主线程直接卡住。
 * 所以内容加 v-if：收起时连 el-table 都不创建。
 */
const expandedKeys = computed(() => new Set(activeKeys.value));

/* ------------------------------------------------------------------ *
 * 表格高度：按需分配
 *
 * 每个表下面有「标签列 / 字段列」两个分区，但真实文件里经常只有一边有数据
 * （树模型文件的 TAG 列恒为 0）。早先的实现把可用高度对半分，空分区白白占掉
 * 一半，有数据的分区只剩一半还被截断在半行位置。
 *
 * 现在改成：空分区只占一行提示文字，剩下的高度全部给有数据的分区；两边都有
 * 数据时先满足自然高度较小的一方。最后把高度对齐到整行，避免最后一行只露半截。
 *
 * 分区顶部到面板底部的可用高度是**实测**的（面板标题、折叠头、小标题的高度
 * 都不需要写死常量），只有实测拿不到时才退回按 `scrollY` 估算。
 * ------------------------------------------------------------------ */

/** 面板内容区（滚动容器），高度实测的基准 */
const bodyRef = ref<HTMLElement | null>(null);

/** 实测：第一个分区顶部到内容区底部的可用高度，0 表示尚未测到 */
const availableHeight = ref(0);
/** 实测：表头高度与单行高度，测不到时用兜底值 */
const headerHeight = ref(40);
const rowHeight = ref(40);

/** 小标题 h4（0.75rem 单行 18px）+ mb-2（8px），实测 24 */
const SECTION_HEADING_HEIGHT = 24;
/** 两个分区之间的 space-y-4 */
const SECTION_GAP = 16;
/** 「未找到标签/字段列」单行提示，实测 20 */
const EMPTY_TEXT_HEIGHT = 20;
/** 内容区的 p-4 下内边距 */
const BODY_PADDING_BOTTOM = 16;
/** 取整行难免有零点几像素误差，留一点余量，宁可少一行也不要撑出滚动条 */
const SAFETY_SLACK = 4;
/** 再挤也要留出的行数 */
const MIN_VISIBLE_ROWS = 3;

/** 实测不可用时的兜底：scrollY 已扣过面板标题等 chrome，这里再扣折叠头与小标题 */
const fallbackAvailable = computed(() => Math.max(200, props.scrollY - 120));

function measure() {
  const body = bodyRef.value;
  if (!body) return;

  const firstSection = body.querySelector<HTMLElement>("[data-section]");
  if (firstSection) {
    const offsetTop =
      firstSection.getBoundingClientRect().top -
      body.getBoundingClientRect().top +
      body.scrollTop;
    availableHeight.value = Math.max(
      0,
      body.clientHeight - offsetTop - BODY_PADDING_BOTTOM - SAFETY_SLACK,
    );
  }

  // 行高/表头高不随 max-height 变化，实测它们不会形成布局回环。
  // 必须取 getBoundingClientRect 的小数高度：行高实际是 40.08px，用取整的
  // offsetHeight 去凑整行会让最后一行被削掉零点几像素，行数多了就看得出来。
  const header = body.querySelector<HTMLElement>(".el-table__header-wrapper");
  const headerH = header?.getBoundingClientRect().height ?? 0;
  if (headerH > 0) headerHeight.value = headerH;
  const row = body.querySelector<HTMLElement>(".el-table__body tr");
  const rowH = row?.getBoundingClientRect().height ?? 0;
  if (rowH > 0) rowHeight.value = rowH;
}

/** 表格装下 n 行需要的高度 */
function naturalHeight(rowCount: number) {
  return headerHeight.value + rowCount * rowHeight.value;
}

/** 向下取整到整行，并夹在 [MIN_VISIBLE_ROWS, rowCount] 之间 */
function snapToRows(height: number, rowCount: number) {
  const rows = Math.floor((height - headerHeight.value) / rowHeight.value);
  const clamped = Math.min(rowCount, Math.max(MIN_VISIBLE_ROWS, rows));
  return naturalHeight(clamped);
}

function sectionHeight(table: Table, kind: "tag" | "field") {
  const tagCount = table.tagColumns.length;
  const fieldCount = table.fieldColumns.length;
  const selfCount = kind === "tag" ? tagCount : fieldCount;
  const otherCount = kind === "tag" ? fieldCount : tagCount;
  if (selfCount === 0) return 0;

  let budget =
    (availableHeight.value > 0 ? availableHeight.value : fallbackAvailable.value) -
    SECTION_HEADING_HEIGHT * 2 -
    SECTION_GAP;
  // 空分区不占表格高度，只占一行提示文字
  if (otherCount === 0) budget -= EMPTY_TEXT_HEIGHT;

  if (otherCount === 0) return snapToRows(budget, selfCount);

  const selfNatural = naturalHeight(selfCount);
  const otherNatural = naturalHeight(otherCount);
  if (selfNatural + otherNatural <= budget) return selfNatural;

  const floorHeight = naturalHeight(MIN_VISIBLE_ROWS);
  const otherMin = Math.min(otherNatural, floorHeight);
  const selfMin = Math.min(selfNatural, floorHeight);
  // 自己能完整装下就不多占；否则若对方能完整装下，余量全给自己
  if (selfNatural <= budget - otherMin) return selfNatural;
  if (otherNatural <= budget - selfMin) return snapToRows(budget - otherNatural, selfCount);
  return snapToRows(budget / 2, selfCount);
}

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  if (!bodyRef.value) return;
  resizeObserver = new ResizeObserver(() => measure());
  resizeObserver.observe(bodyRef.value);
  void nextTick(measure);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
});

// 折叠展开与数据变化都会改变第一个分区的位置，需要重新实测
watch([activeKeys, () => props.tables], () => void nextTick(measure), { deep: true });

// 默认只展开第一张表：表多的文件全部展开既没有可读性，也会把折叠区撑到几万像素高。
// 表集合变化（换文件）时同样重置，否则会残留上一个文件的展开项、看起来一张都没展开。
watch(
  () => props.tables,
  (tables) => {
    activeKeys.value = tables.length > 0 ? [tables[0].tableName] : [];
  },
  { immediate: true },
);
</script>

<template>
  <div class="tc-panel flex h-full flex-col">
    <div class="tc-panel-title flex-shrink-0">
      <span>
        {{ t("tsfile.metadata.tables") }}
        <span class="ml-2 text-xs text-text-body tnum">({{ tables.length }})</span>
      </span>
    </div>

    <div ref="bodyRef" v-loading="loading" class="min-h-0 flex-1 overflow-y-auto p-4">
      <!--
        旧组件库是 :items 配置 + #contentRender 里按 key 反查表对象；
        这里直接 v-for 绑定 table，去掉运行时反查。
      -->
      <el-collapse v-if="tables.length > 0" v-model="activeKeys">
        <el-collapse-item
          v-for="table in tables"
          :key="table.tableName"
          :name="table.tableName"
        >
          <template #title>
            <span class="text-text-heading">{{ table.tableName }}</span>
            <span class="ml-2 text-xs text-text-body tnum">
              (Tag: {{ table.tagColumns.length }}, Field: {{ table.fieldColumns.length }})
            </span>
          </template>

          <template v-if="expandedKeys.has(table.tableName)">
            <div class="space-y-4">
              <!-- 标签列 -->
              <div data-section>
                <h4 class="mb-2 text-xs font-normal uppercase tracking-wider text-text-body">
                  {{ t("tsfile.metadata.tagColumnsDesc") }}
                </h4>
                <div v-if="table.tagColumns.length > 0" class="tc-table-card">
                  <el-table
                    v-bind="tableStyleProps"
                    :data="table.tagColumns"
                    :max-height="sectionHeight(table, 'tag')"
                    row-key="name"
                    size="small"
                    border
                  >
                    <el-table-column
                      :label="t('tsfile.metadata.columnName')"
                      prop="name"
                      show-overflow-tooltip
                    />
                    <el-table-column :label="t('tsfile.metadata.dataType')" :width="120">
                      <template #default="{ row }">
                        <el-tag :type="getDataTypeTagType(row.dataType)" size="small">
                          {{ row.dataType }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column
                      :label="t('tsfile.metadata.encoding')"
                      prop="encoding"
                      :width="120"
                    />
                    <el-table-column
                      :label="t('tsfile.metadata.compression')"
                      prop="compression"
                      :width="120"
                    />
                  </el-table>
                </div>
                <div v-else class="text-sm text-text-body">
                  {{ t("tsfile.metadata.noTagColumns") }}
                </div>
              </div>

              <!-- 字段列 -->
              <div data-section>
                <h4 class="mb-2 text-xs font-normal uppercase tracking-wider text-text-body">
                  {{ t("tsfile.metadata.fieldColumnsDesc") }}
                </h4>
                <div v-if="table.fieldColumns.length > 0" class="tc-table-card">
                  <el-table
                    v-bind="tableStyleProps"
                    :data="table.fieldColumns"
                    :max-height="sectionHeight(table, 'field')"
                    row-key="name"
                    size="small"
                    border
                  >
                    <el-table-column
                      :label="t('tsfile.metadata.columnName')"
                      prop="name"
                      show-overflow-tooltip
                    />
                    <el-table-column :label="t('tsfile.metadata.dataType')" :width="120">
                      <template #default="{ row }">
                        <el-tag :type="getDataTypeTagType(row.dataType)" size="small">
                          {{ row.dataType }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column
                      :label="t('tsfile.metadata.encoding')"
                      prop="encoding"
                      :width="120"
                    />
                    <el-table-column
                      :label="t('tsfile.metadata.compression')"
                      prop="compression"
                      :width="120"
                    />
                  </el-table>
                </div>
                <div v-else class="text-sm text-text-body">
                  {{ t("tsfile.metadata.noFieldColumns") }}
                </div>
              </div>
            </div>
          </template>
        </el-collapse-item>
      </el-collapse>

      <el-empty v-else :description="t('tsfile.metadata.noTables')" :image-size="72" />
    </div>
  </div>
</template>

<style scoped>
/*
 * el-collapse-item 的内容区默认有 25px 下内边距，面板自身的 p-4 已经提供了
 * 底部留白，这里清掉，把高度还给表格（否则每个表都要凭空少半行）。
 */
.tc-panel :deep(.el-collapse-item__content) {
  padding-bottom: 0;
}
</style>
