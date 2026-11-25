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
 * FileViewNav - 单个 TsFile 的三个功能视图切换按钮组。
 *
 * 三个功能页显示同一组按钮，位置固定：返回 / 元数据 / 图表可视化 / 数据预览。
 * 「数据预览」是各页的默认落地页，始终蓝底白字并固定在最右，其余按钮白底黑字。
 * 之前每页各写一套，高亮既会指到别的功能上，按钮位置也随页面变动（当前功能被排到最右），
 * 这里收敛到一处并固定顺序，切换页面时按钮不跳位。
 */
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";

const props = defineProps<{ fileId: string }>();

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const metadataPath = computed(() => `/tsfile/meta/${props.fileId}`);
const chartPath = computed(() => `/tsfile/chart/${props.fileId}`);
const dataPath = computed(() => `/tsfile/data/${props.fileId}`);

function goBack() {
  router.push("/tsfile/files");
}

/** 已经在当前页再点一次是空导航，vue-router 会抛重复导航，这里提前跳过 */
function goTo(path: string) {
  if (route.path !== path) router.push(path);
}
</script>

<template>
  <el-button @click="goBack">{{ t("tsfile.common.back") }}</el-button>
  <el-button @click="goTo(metadataPath)">{{ t("tsfile.metadata.title") }}</el-button>
  <el-button @click="goTo(chartPath)">{{ t("tsfile.chart.title") }}</el-button>
  <el-button type="primary" @click="goTo(dataPath)">{{ t("tsfile.data.title") }}</el-button>
</template>
