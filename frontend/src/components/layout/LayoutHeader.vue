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
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { computed } from 'vue';

import FullscreenToggle from './FullscreenToggle.vue';
import LanguageToggle from './LanguageToggle.vue';
import RefreshButton from './RefreshButton.vue';
import ThemeToggle from './ThemeToggle.vue';
import TimezoneSelector from './TimezoneSelector.vue';

const router = useRouter();
const route = useRoute();
const { t } = useI18n();

const currentRouteName = computed(() => route.name as string);

function navigateToFiles() {
  router.push({ name: 'FileSelection' });
}
function navigateToScan() {
  router.push({ name: 'FileScan' });
}
</script>

<template>
  <div style="display: flex; align-items: center; justify-content: space-between; height: 48px; padding: 0 16px; width: 100%;">
    <!-- Left: Logo + Title + Nav -->
    <div style="display: flex; align-items: center; gap: 24px; flex-shrink: 0;">
      <div style="font-size: 16px; font-weight: bold; color: #fff; white-space: nowrap; cursor: pointer;" @click="navigateToFiles">
        TsFile Viewer
      </div>
      <nav style="display: flex; align-items: center; gap: 4px;">
        <span
          style="padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 14px; transition: background 0.2s;"
          :style="{ color: '#fff', background: currentRouteName === 'FileSelection' ? 'rgba(255,255,255,0.15)' : 'transparent' }"
          @click="navigateToFiles"
        >
          {{ t('tsfile.nav.files') }}
        </span>
        <span
          style="padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 14px; transition: background 0.2s;"
          :style="{ color: '#fff', background: currentRouteName === 'FileScan' ? 'rgba(255,255,255,0.15)' : 'transparent' }"
          @click="navigateToScan"
        >
          {{ t('tsfile.scan.title') }}
        </span>
      </nav>
    </div>

    <!-- Right: Widget Bar -->
    <div style="display: flex; align-items: center; gap: 2px; flex-shrink: 0;">
      <ThemeToggle />
      <LanguageToggle />
      <TimezoneSelector />
      <FullscreenToggle />
      <RefreshButton />
    </div>
  </div>
</template>
