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
import type { TsFileMetadata } from "@/api/tsfile/types";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { metaApi } from "@/api/tsfile";
import MetaCards from "@/components/tsfile/MetaCards.vue";
import MeasurementsTable from "@/components/tsfile/MeasurementsTable.vue";
import RowGroupsTable from "@/components/tsfile/RowGroupsTable.vue";
import TablesTable from "@/components/tsfile/TablesTable.vue";
import PageHeader from "@/components/layout/PageHeader.vue";
import { useFileStore } from "@/stores/tsfile/file";
import { decodeFileId } from "@/utils/fileId";

const route = useRoute();
const router = useRouter();
const fileStore = useFileStore();
const { t } = useI18n();

// 页面刷新时从 localStorage 恢复 currentFile，必须在 watcher (immediate) 之前执行
fileStore.restoreCurrentFile();

const fileId = computed(() => route.params.fileId as string);
const metadata = ref<TsFileMetadata | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const activeTab = ref("rowGroups");

const displayFileName = computed(() => {
  if (fileStore.currentFileName) return fileStore.currentFileName;
  // Uploaded-file fileId 是 UUID（最多 32 位 hex），不是 base64 编码的路径，
  // 对其调用 decodeFileId 会产生乱码，直接返回 fileId 让用户知道当前文件标识。
  if (/^[0-9a-fA-F]{8,32}$/.test(fileId.value)) return fileId.value;
  try {
    const decoded = decodeFileId(fileId.value);
    // split 同时支持 Unix 和 Windows 路径分隔符
    return decoded.split(/[/\\]/).pop() || fileId.value;
  } catch {
    return fileId.value;
  }
});

const isTableModel = computed(() => metadata.value?.tables && metadata.value.tables.length > 0);

const tabItems = computed(() => {
  const items: Array<{ key: string; label: string }> = [];
  if (isTableModel.value) {
    items.push({ key: 'tables', label: t('tsfile.metadata.tables') });
  }
  items.push({ key: 'rowGroups', label: t('tsfile.metadata.rowGroups') });
  items.push({
    key: 'measurements',
    label: isTableModel.value ? 'Field' : t('tsfile.metadata.measurements'),
  });
  return items;
});

watch(
  fileId,
  (newId, oldId) => {
    if (newId && newId !== oldId) {
      metadata.value = null;
      error.value = null;
      loadMetadata();
    }
  },
  { immediate: true },
);

async function loadMetadata() {
  if (!fileId.value) return;
  loading.value = true;
  error.value = null;
  try {
    metadata.value = (await metaApi.getMetadata(fileId.value)) as TsFileMetadata;
    // Set default tab based on model type
    activeTab.value = (metadata.value?.tables && metadata.value.tables.length > 0) ? 'tables' : 'rowGroups';
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : "Failed to load metadata";
  } finally {
    loading.value = false;
  }
}

function goToDataPreview() {
  router.push(`/tsfile/data/${fileId.value}`);
}
function goToChart() {
  router.push(`/tsfile/chart/${fileId.value}`);
}
function goBack() {
  router.push("/tsfile/files");
}
function goToQuickScan() {
  try {
    const filePath = decodeFileId(fileId.value);
    fileStore.setScanTarget(filePath, 'file', true);
    router.push('/tsfile/scan');
  } catch {
    router.push('/tsfile/scan');
  }
}

const tabContentRef = ref<HTMLElement | null>(null);
const tabContentHeight = ref(400);

// 兜底像素值，仅在表格 DOM 还没渲染出来时使用。
// 现在三个表格都是 tc-panel + el-table：面板标题栏 ~48px + 面板内边距 16px
// + el-table(size=small) 表头 ~34px ≈ 100px，留一点余量取 108。
// 迁移前是 Card+Table 的 144px，这里已按新 DOM 重新标定。
const FALLBACK_CHROME_HEIGHT = 108;

// 实测值的上限。TablesTable 的表格嵌在 el-collapse 里，层层标题会让
// "表体距容器顶部的距离"远大于真实 chrome，若照实减掉表格只剩一两行高。
// 超过这个值就认为实测不可信，回退到兜底值。
const MAX_TRUSTED_CHROME_HEIGHT = 180;

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  if (tabContentRef.value) {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const container = entry.target as HTMLElement;
        // 优先实测：找到当前渲染的表格滚动区域顶部相对容器顶部的偏移，
        // 即"标题栏 + 表头"等 chrome 的真实高度。实测优先意味着上方增删
        // 元素时不需要再改常量。
        const scrollBody = container.querySelector<HTMLElement>(
          ".el-table__body-wrapper",
        );
        const measured = scrollBody
          ? Math.max(
              0,
              scrollBody.getBoundingClientRect().top -
                container.getBoundingClientRect().top,
            )
          : FALLBACK_CHROME_HEIGHT;
        const chromeHeight =
          measured > MAX_TRUSTED_CHROME_HEIGHT ? FALLBACK_CHROME_HEIGHT : measured;
        const available = entry.contentRect.height - chromeHeight;
        tabContentHeight.value = Math.max(200, available);
      }
    });
    resizeObserver.observe(tabContentRef.value);
  }
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
});
</script>

<template>
  <div class="flex flex-col h-full">
    <PageHeader :title="t('tsfile.metadata.title')" :subtitle="displayFileName">
      <template #actions>
        <el-button @click="goBack">{{ t("tsfile.common.back") }}</el-button>
        <el-button type="primary" @click="goToDataPreview">{{ t("tsfile.data.title") }}</el-button>
        <el-button @click="goToChart">{{ t("tsfile.chart.title") }}</el-button>
      </template>
    </PageHeader>
    <template v-if="error">
      <div class="tc-panel mb-3">
        <div class="flex items-start justify-between gap-3 p-4">
          <div class="min-w-0">
            <p class="font-medium text-danger">{{ t("tsfile.error.loadFailed") }}</p>
            <p class="mt-1 text-sm text-text-body">{{ error }}</p>
          </div>
          <el-button size="small" type="danger" class="flex-shrink-0" @click="goToQuickScan">
            {{ t("tsfile.scan.quickScan") }}
          </el-button>
        </div>
      </div>
    </template>
    <template v-else>
      <div class="flex-shrink-0 mb-3">
        <MetaCards :metadata="metadata" :loading="loading" />
      </div>
      <div class="flex-shrink-0">
        <el-tabs v-model="activeTab" type="card" class="tc-meta-tabs">
          <el-tab-pane
            v-for="item in tabItems"
            :key="item.key"
            :name="item.key"
            :label="item.label"
          />
        </el-tabs>
      </div>
      <div ref="tabContentRef" class="flex-1 min-h-0 overflow-hidden border border-solid border-border-default border-t-0 rounded-b-lg bg-bg-card">
        <TablesTable v-if="activeTab === 'tables' && isTableModel" :tables="metadata?.tables || []" :loading="loading" :scroll-y="tabContentHeight" />
        <MeasurementsTable v-if="activeTab === 'measurements'" :measurements="metadata?.measurements || []" :loading="loading" :scroll-y="tabContentHeight" />
        <RowGroupsTable v-if="activeTab === 'rowGroups'" :row-groups="metadata?.rowGroups || []" :loading="loading" :scroll-y="tabContentHeight" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.tc-meta-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
</style>
