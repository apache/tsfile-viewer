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
 * MetaCards 组件 - 元数据基本信息卡片
 * 显示文件版本、时间范围、设备数量、测点数量等
 */
import type { TsFileMetadata } from "@/api/tsfile/types";

import { computed } from "vue";
import { useI18n } from "vue-i18n";

import { formatTimestamp } from "@/utils/timestamp";

interface Props {
  metadata: TsFileMetadata | null;
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const { t } = useI18n();

const isTableModel = computed(() => {
  return props.metadata?.tables && props.metadata.tables.length > 0;
});

const modelTypeLabel = computed(() => {
  return isTableModel.value
    ? t("tsfile.metadata.tableModel")
    : t("tsfile.metadata.treeModel");
});

const items = computed(() => {
  if (!props.metadata) return [];

  const result: Array<{ key: string; label: string; content: string }> = [
    {
      key: "version",
      label: t("tsfile.metadata.version"),
      content: props.metadata.version,
    },
    {
      key: "timeRange",
      label: t("tsfile.metadata.timeRange"),
      content: `${formatTimestamp(props.metadata.timeRange.startTime)} → ${formatTimestamp(props.metadata.timeRange.endTime)}`,
    },
  ];

  if (isTableModel.value) {
    result.push({
      key: "tableCount",
      label: t("tsfile.metadata.tableCount"),
      content: String(props.metadata.tables?.length || 0),
    });
  }

  result.push(
    {
      key: "deviceCount",
      label: t("tsfile.metadata.deviceCount"),
      content: String(props.metadata.deviceCount),
    },
    {
      key: "measurementCount",
      label: t("tsfile.metadata.measurementCount"),
      content: String(props.metadata.measurementCount),
    },
    {
      key: "chunkCount",
      label: t("tsfile.metadata.chunkCount"),
      content: String(props.metadata.chunkCount),
    },
  );

  return result;
});
</script>

<template>
  <div class="tc-panel">
    <div class="tc-panel-title">
      <span>{{ t("tsfile.metadata.basicInfo") }}</span>
      <!-- 旧组件库的 color="processing" 是其特有语义色，Element Plus 用 primary 代替 -->
      <el-tag v-if="metadata" :type="isTableModel ? 'success' : 'primary'" size="small">
        {{ modelTypeLabel }}
      </el-tag>
    </div>

    <div v-loading="loading" class="p-5">
      <el-descriptions v-if="metadata" :column="2" border>
        <el-descriptions-item
          v-for="item in items"
          :key="item.key"
          :label="item.label"
        >
          <span class="tnum">{{ item.content }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-empty v-else :description="t('tsfile.common.noData')" :image-size="72" />
    </div>
  </div>
</template>
