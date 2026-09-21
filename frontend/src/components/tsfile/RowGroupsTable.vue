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
 * RowGroupsTable 组件 - RowGroup 列表表格
 * 显示设备、时间范围、Chunk 数量
 */
import type { RowGroup } from "@/api/tsfile/types";

import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";

import { Search } from "lucide-vue-next";

import { tableStyleProps } from "@/utils/tableStyle";
import { formatTimestamp } from "@/utils/timestamp";

interface Props {
  rowGroups: RowGroup[];
  loading?: boolean;
  scrollY?: number;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  scrollY: 400,
});

const { t } = useI18n();

const searchQuery = ref("");

// 过滤后的 RowGroup 列表
const filteredRowGroups = computed(() => {
  if (!searchQuery.value) {
    return props.rowGroups;
  }
  const query = searchQuery.value.toLowerCase();
  return props.rowGroups.filter((rg) => rg.device?.toLowerCase().includes(query));
});
</script>

<template>
  <div class="tc-panel">
    <div class="tc-panel-title">
      <span>
        {{ t("tsfile.metadata.rowGroups") }}
        <span class="ml-2 text-xs text-text-body tnum">
          ({{ filteredRowGroups.length }} / {{ rowGroups.length }})
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

    <div class="p-4">
      <div class="tc-table-card">
        <!--
          Element Plus 的 el-table 没有虚拟滚动（旧组件库的 :virtual 无对应能力），
          这里改用 max-height 让表格自身滚动；RowGroup 数量通常在千级以内可以承受。
        -->
        <el-table
          v-bind="tableStyleProps"
          v-loading="loading"
          :data="filteredRowGroups"
          :max-height="props.scrollY"
          row-key="index"
          size="small"
          border
          :empty-text="t('tsfile.metadata.noRowGroups')"
        >
          <el-table-column
            :label="t('tsfile.metadata.rowGroupIndex')"
            prop="index"
            :width="80"
            align="center"
          />
          <el-table-column
            :label="t('tsfile.metadata.device')"
            prop="device"
            show-overflow-tooltip
          />
          <el-table-column :label="t('tsfile.metadata.timeRange')">
            <template #default="{ row }">
              <span class="font-mono text-xs">{{ formatTimestamp(row.startTime) }}</span>
              <span class="mx-2 text-text-body">~</span>
              <span class="font-mono text-xs">{{ formatTimestamp(row.endTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('tsfile.metadata.chunkCount')"
            prop="chunkCount"
            :width="120"
            align="center"
          />
        </el-table>
      </div>
    </div>
  </div>
</template>
