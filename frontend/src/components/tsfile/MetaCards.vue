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

import { Card, Descriptions, Spin, Tag } from "antdv-next";

interface Props {
  metadata: TsFileMetadata | null;
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const { t } = useI18n();

function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleString();
}

const isTableModel = computed(() => {
  return props.metadata?.tables && props.metadata.tables.length > 0;
});

const modelTypeLabel = computed(() => {
  return isTableModel.value ? t("tsfile.metadata.tableModel") : t("tsfile.metadata.treeModel");
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
      content: `${formatTime(props.metadata!.timeRange.startTime)} → ${formatTime(props.metadata!.timeRange.endTime)}`,
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
  <Card>
    <template #title>
      <div class="flex items-center justify-between">
        <span class="font-semibold">{{ t("tsfile.metadata.basicInfo") }}</span>
        <Tag v-if="metadata" :color="isTableModel ? 'success' : 'processing'">
          {{ modelTypeLabel }}
        </Tag>
      </div>
    </template>

    <Spin :spinning="loading">
      <Descriptions v-if="metadata" :items="items" :column="2" bordered />

      <div v-else class="py-8 text-center text-gray-500">
        {{ t("tsfile.common.noData") }}
      </div>
    </Spin>
  </Card>
</template>
