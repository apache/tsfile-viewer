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
 * FileViewNav - 单个 TsFile 的三视图切换按钮组。
 *
 * 三个功能页的按钮结构完全一致：返回 + 另外两个功能 + 当前功能（蓝底白字、排最右），
 * 之前每页各写一套，高亮落在哪个按钮上并不一致（数据预览页高亮的是"图表可视化"），
 * 因此收敛到一处，新增功能页时也不会漏改。
 */
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";

type FileView = "metadata" | "data" | "chart";

const props = defineProps<{
  fileId: string;
  /** 当前所在的功能页，只有它的按钮是蓝底白字 */
  active: FileView;
}>();

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const views = computed(() => [
  { id: "metadata" as FileView, label: t("tsfile.metadata.title"), path: `/tsfile/meta/${props.fileId}` },
  { id: "data" as FileView, label: t("tsfile.data.title"), path: `/tsfile/data/${props.fileId}` },
  { id: "chart" as FileView, label: t("tsfile.chart.title"), path: `/tsfile/chart/${props.fileId}` },
]);

// 当前功能固定排最右，其余功能之间的相对顺序不动，切换页面时按钮不跳位
const others = computed(() => views.value.filter((view) => view.id !== props.active));
const current = computed(() => views.value.find((view) => view.id === props.active)!);

function goBack() {
  router.push("/tsfile/files");
}

/** 当前页按钮再点一次是空导航，vue-router 会抛重复导航，这里提前跳过 */
function goTo(path: string) {
  if (route.path !== path) router.push(path);
}
</script>

<template>
  <el-button @click="goBack">{{ t("tsfile.common.back") }}</el-button>
  <el-button v-for="view in others" :key="view.id" @click="goTo(view.path)">{{ view.label }}</el-button>
  <el-button type="primary" @click="goTo(current.path)">{{ current.label }}</el-button>
</template>
