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
 * FileViewNav - 单个 TsFile 的顶部横向菜单。
 *
 * 三个功能页共用一条菜单，条目与顺序固定：返回 | 元数据 | 图表可视化 | 数据预览。
 * 高亮的是「当前所在页」（菜单惯例），所以切换页面时条目位置不动、只有下划线移动。
 * 之前是标题栏右侧的一组按钮：位置随页面变，实心按钮也和内容区的操作按钮混淆。
 */
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { ArrowLeft } from "lucide-vue-next";

type FileView = "metadata" | "data" | "chart";

const props = defineProps<{
  fileId: string;
  /** 当前所在的功能页 */
  active: FileView;
}>();

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const views = computed(() => [
  { id: "data" as FileView, label: t("tsfile.data.title"), path: `/tsfile/data/${props.fileId}` },
  { id: "chart" as FileView, label: t("tsfile.chart.title"), path: `/tsfile/chart/${props.fileId}` },
  { id: "metadata" as FileView, label: t("tsfile.metadata.title"), path: `/tsfile/meta/${props.fileId}` },
]);

function goBack() {
  router.push("/tsfile/files");
}

/** 当前页再点一次是空导航，vue-router 会抛重复导航，这里提前跳过 */
function goTo(path: string) {
  if (route.path !== path) router.push(path);
}
</script>

<template>
  <nav class="file-view-nav">
    <button type="button" class="file-view-nav__item file-view-nav__item--back" @click="goBack">
      <ArrowLeft class="h-4 w-4" :stroke-width="1.75" />
      <span>{{ t("tsfile.common.back") }}</span>
    </button>
    <span class="file-view-nav__divider" aria-hidden="true"></span>
    <button
      v-for="view in views"
      :key="view.id"
      type="button"
      class="file-view-nav__item"
      :class="{ 'is-active': view.id === active }"
      @click="goTo(view.path)"
    >
      {{ view.label }}
    </button>
  </nav>
</template>

<style scoped>
/* 菜单条：底部一条细线，条目用下划线表示当前页，和左侧栏的点选态区分开 */
.file-view-nav {
  display: flex;
  align-items: center;
  gap: 0.125rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid var(--border-default);
}

.file-view-nav__item {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  margin-bottom: -1px;
  padding: 0.5rem 0.75rem;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--text-label);
  font-size: 0.875rem;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background-color 0.15s ease,
    border-color 0.15s ease;
}

.file-view-nav__item:hover {
  background-color: color-mix(in srgb, var(--primary) 5%, transparent);
  color: var(--text-heading);
}

.file-view-nav__item.is-active {
  border-bottom-color: var(--primary);
  color: var(--primary);
}

/* 返回离开的是「文件」这一层，不是同级功能页，用更弱的颜色和一根分隔线区分 */
.file-view-nav__item--back {
  color: var(--text-body);
}

.file-view-nav__divider {
  width: 1px;
  height: 1rem;
  margin: 0 0.375rem;
  background-color: var(--border-default);
}
</style>
