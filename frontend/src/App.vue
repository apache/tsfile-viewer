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
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";

import { ElConfigProvider } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import en from "element-plus/es/locale/lang/en";
import { Menu } from "lucide-vue-next";

import AppSidebar from "./components/layout/AppSidebar.vue";
import FileTree from "./components/tsfile/FileTree.vue";
import { useFileStore } from "./stores/tsfile/file";
import { useTheme } from "./composables/useTheme";

const router = useRouter();
const route = useRoute();
const fileStore = useFileStore();
const { locale } = useI18n();

// 挂载 useTheme 以建立 <html>.dark 的同步与系统偏好监听
useTheme();

const sidebarOpen = ref(false);

const isOnScanPage = computed(() => route.name === "FileScan");

/** 当前所在的功能页；不在三个功能页上时为 null（文件列表、扫描页等） */
const currentFileView = computed(() => {
  const name = route.name;
  if (name === "DataPreview" || name === "ChartVisualization" || name === "Metadata") return name;
  return null;
});

const elementLocale = computed(() => (locale.value === "zh-CN" ? zhCn : en));

function handleFileSelect(fileId: string, path: string, name: string) {
  if (isOnScanPage.value) {
    fileStore.setScanTarget(path, "file");
  } else {
    fileStore.setCurrentFile(fileId, name);
    // 在功能页里换文件时停留在当前页面，只换 fileId；不在功能页时默认进数据预览
    if (currentFileView.value === "ChartVisualization") router.push(`/tsfile/chart/${fileId}`);
    else if (currentFileView.value === "Metadata") router.push(`/tsfile/meta/${fileId}`);
    else router.push(`/tsfile/data/${fileId}`);
  }
}

function handleDirectorySelect(path: string, _name: string) {
  if (isOnScanPage.value) {
    fileStore.setScanTarget(path, "directory");
  }
}
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <div class="flex h-screen overflow-hidden bg-bg-app">
      <AppSidebar :open="sidebarOpen" @close="sidebarOpen = false">
        <template #tree>
          <FileTree @select="handleFileSelect" @select-directory="handleDirectorySelect" />
        </template>
      </AppSidebar>

      <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
        <!-- 窄屏顶部条 -->
        <header
          class="flex h-12 flex-shrink-0 items-center gap-3 border-b border-border-default bg-bg-card px-4 lg:hidden"
        >
          <button
            type="button"
            class="tc-nav-item !p-1.5"
            aria-label="Toggle navigation"
            @click="sidebarOpen = !sidebarOpen"
          >
            <Menu class="h-5 w-5" :stroke-width="1.75" />
          </button>
          <span class="text-[0.9375rem] text-text-heading">TsFile Viewer</span>
        </header>

        <main class="flex flex-1 flex-col overflow-y-auto p-4 lg:p-6">
          <RouterView />
        </main>
      </div>
    </div>
  </ElConfigProvider>
</template>
