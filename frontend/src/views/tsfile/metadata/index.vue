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
import { Alert, Button, Space, Tabs } from "antdv-next";
import { metaApi } from "@/api/tsfile";
import MetaCards from "@/components/tsfile/MetaCards.vue";
import MeasurementsTable from "@/components/tsfile/MeasurementsTable.vue";
import RowGroupsTable from "@/components/tsfile/RowGroupsTable.vue";
import TablesTable from "@/components/tsfile/TablesTable.vue";
import { useFileStore } from "@/stores/tsfile/file";

const route = useRoute();
const router = useRouter();
const fileStore = useFileStore();
const { t } = useI18n();

const fileId = computed(() => route.params.fileId as string);
const metadata = ref<TsFileMetadata | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const activeTab = ref("rowGroups");

const displayFileName = computed(() => {
  if (fileStore.currentFileName) return fileStore.currentFileName;
  try {
    const decoded = atob(fileId.value);
    return decoded.split('/').pop() || fileId.value;
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
    const filePath = atob(fileId.value);
    fileStore.setScanTarget(filePath, 'file', true);
    router.push('/tsfile/scan');
  } catch {
    router.push('/tsfile/scan');
  }
}

const tabContentRef = ref<HTMLElement | null>(null);
const tabContentHeight = ref(400);

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  if (tabContentRef.value) {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        // Subtract Card title (~56px) + Card padding (~48px) + table header (~40px)
        const available = entry.contentRect.height - 144;
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
    <div class="flex items-center justify-between mb-3 flex-shrink-0">
      <div>
        <h2 class="text-xl font-bold">{{ t("tsfile.metadata.title") }}</h2>
        <p class="text-gray-500 text-sm truncate max-w-lg">{{ displayFileName }}</p>
      </div>
      <div class="flex gap-2">
        <Button @click="goBack">{{ t("tsfile.common.back") }}</Button>
        <Button type="primary" @click="goToDataPreview">{{ t("tsfile.data.title") }}</Button>
        <Button @click="goToChart">{{ t("tsfile.chart.title") }}</Button>
      </div>
    </div>
    <template v-if="error">
      <Alert type="error" show-icon :message="t('tsfile.error.loadFailed')" :description="error" class="mb-3">
        <template #action>
          <Button size="small" type="primary" danger @click="goToQuickScan">{{ t('tsfile.scan.quickScan') }}</Button>
        </template>
      </Alert>
    </template>
    <template v-else>
      <div class="flex-shrink-0 mb-3">
        <MetaCards :metadata="metadata" :loading="loading" />
      </div>
      <div class="flex-shrink-0">
        <Tabs v-model:activeKey="activeTab" type="card" :items="tabItems" :tabBarStyle="{ marginBottom: 0 }" />
      </div>
      <div ref="tabContentRef" class="flex-1 min-h-0 overflow-hidden border border-solid border-gray-200 border-t-0 rounded-b-lg bg-white dark:bg-gray-900 dark:border-gray-700">
        <TablesTable v-if="activeTab === 'tables' && isTableModel" :tables="metadata?.tables || []" :loading="loading" :scroll-y="tabContentHeight" />
        <MeasurementsTable v-if="activeTab === 'measurements'" :measurements="metadata?.measurements || []" :loading="loading" :scroll-y="tabContentHeight" />
        <RowGroupsTable v-if="activeTab === 'rowGroups'" :row-groups="metadata?.rowGroups || []" :loading="loading" :scroll-y="tabContentHeight" />
      </div>
    </template>
  </div>
</template>
