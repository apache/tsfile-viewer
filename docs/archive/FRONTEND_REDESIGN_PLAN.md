# TsFile Viewer 前端改造计划

## 概述

本文档基于当前前端代码分析，提供详细的UI/UX改造建议和实施计划，旨在提升用户体验、视觉设计和代码可维护性。

---

## 目录

1. [当前状态分析](#当前状态分析)
2. [核心问题](#核心问题)
3. [设计原则](#设计原则)
4. [改造计划](#改造计划)
5. [实施路线图](#实施路线图)
6. [技术栈优化](#技术栈优化)

---

## 当前状态分析

### 技术栈
- ✅ **Vue 3.5** (Composition API) - 现代化
- ✅ **Nuxt UI** - 组件库基础良好
- ✅ **Pinia** - 状态管理清晰
- ✅ **TypeScript** - 类型安全
- ✅ **i18n** - 国际化支持完善
- ✅ **ECharts 6** - 强大的图表能力

### 现有组件结构
```
views/
├── FileSelectionView.vue     ✅ 功能完整
├── MetadataView.vue          ✅ 功能完整  
├── DataPreviewView.vue       ⚠️  需要优化（过滤区域）
├── ChartVisualizationView.vue ⚠️  需要重构（布局混乱）

components/
├── FilterPanel.vue           ❌ 布局差，用户体验不佳
├── DataTable.vue            ⚠️  基础功能完整，缺少高级特性
├── ChartPanel.vue           ⚠️  缺少工具栏和交互功能
├── MetaCards.vue            ✅ 设计良好
├── FileTree.vue             ✅ 功能完整
```

### 代码质量评估

**优点**：
- Composition API使用规范
- 组件职责划分清晰
- TypeScript类型定义完整
- 国际化覆盖全面

**缺点**：
- FilterPanel布局原始（从左到右单行排列）
- ChartVisualizationView控件排列混乱（grid但不响应）
- 缺少统一的设计系统（间距、颜色、字体不一致）
- 交互反馈不足（loading状态、错误提示）
- 缺少空状态设计
- 移动端适配不足

---

## 核心问题

### 1. FilterPanel - 最严重的UI问题

**当前实现**：
```vue
<div class="grid gap-4" :class="isTableModel ? 'grid-cols-3' : 'grid-cols-2'">
  <!-- Table, Device, Measurement 选择器 -->
</div>
<div class="grid grid-cols-2 gap-4">
  <!-- Start Time, End Time -->
</div>
<div class="flex gap-3">
  <!-- Apply, Reset 按钮 -->
</div>
```

**问题**：
- 在窄屏上3列布局拥挤
- 没有响应式断点
- 没有折叠/展开功能
- 高级过滤选项缺失（值范围过滤）
- 视觉层次不明显
- 缺少过滤条件预览

**影响**：
- 用户很难找到想要的过滤选项
- 在1920px宽屏上过滤器被挤在一起
- 移动端几乎无法使用

### 2. ChartVisualizationView - 布局混乱

**当前实现**：
```vue
<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
  <UCard><!-- Measurements --></UCard>
  <UCard><!-- Start Time --></UCard>
  <UCard><!-- End Time --></UCard>
  <UCard><!-- Aggregation --></UCard>
  <UCard><!-- Window Size (conditional) --></UCard>
  <UCard><!-- Max Points --></UCard>
  <UCard class="md:col-span-2"><!-- Apply Button --></UCard>
</div>
```

**问题**：
- 每个输入都是单独的Card，视觉噪音大
- 条件显示的Window Size导致布局跳动
- 没有逻辑分组（时间相关、聚合相关）
- Apply按钮占2列，不协调
- 缺少快捷时间范围选择（Last 1h, Last 24h等）

### 3. DataTable - 功能性问题

**当前实现**：
```vue
<UTable :data="tableData" :columns="tableColumns" 
        :loading="loading" class="max-h-[312px]">
```

**问题**：
- 固定高度312px不够灵活
- 缺少列排序
- 缺少列可见性控制
- 没有行选择功能
- 导出功能只有按钮，没有进度反馈
- 缺少虚拟滚动（大数据集性能差）

### 4. 整体设计系统缺失

**问题**：
- 间距不一致（gap-2, gap-3, gap-4混用）
- Card padding不统一
- 按钮大小不一致（sm, md, lg混用）
- 颜色使用随意（primary, outline, ghost混用）
- 缺少统一的错误/成功/警告状态设计

---

## 设计原则

### 1. 用户体验优先
- **减少认知负担**：相关控件分组，逻辑流程清晰
- **即时反馈**：Loading、错误、成功状态明确
- **容错设计**：输入验证、错误提示、默认值
- **快捷操作**：常用功能一键达到

### 2. 响应式设计
- **移动优先**：小屏幕体验优良
- **断点管理**：sm(640px), md(768px), lg(1024px), xl(1280px)
- **自适应布局**：内容根据屏幕大小调整
- **触摸友好**：按钮大小>=44px，间距>=8px

### 3. 视觉一致性
- **设计令牌**：统一的间距、颜色、字体大小
- **组件复用**：统一的Card、Button、Input样式
- **视觉层次**：大小、颜色、间距体现重要性
- **品牌色彩**：主色、辅色、状态色明确定义

### 4. 性能优化
- **虚拟滚动**：大列表使用虚拟化
- **懒加载**：按需加载组件和数据
- **防抖节流**：搜索、过滤输入防抖
- **缓存策略**：合理使用前端缓存

---

## 改造计划

### 阶段一：FilterPanel 重构（优先级：🔴 最高）

#### 目标
将混乱的过滤面板改造为现代化、易用的多层过滤界面。

#### 设计方案

**新布局结构**：
```
┌─────────────────────────────────────────────────────┐
│ Filters                                    [Expand] │
├─────────────────────────────────────────────────────┤
│ ┌─ Basic Filters ────────────────────────────────┐ │
│ │ [Table ▼] [Device ▼] [Measurements ▼]         │ │
│ │  响应式3列（lg: 3列，md: 2列，sm: 1列）            │ │
│ └────────────────────────────────────────────────┘ │
│                                                     │
│ ┌─ Time Range ───────────────────────────────────┐ │
│ │ Quick Select: [1h] [6h] [24h] [7d] [30d]      │ │
│ │ Custom: [Start Time] [End Time]                │ │
│ │  响应式2列（md: 2列，sm: 1列）                     │ │
│ └────────────────────────────────────────────────┘ │
│                                                     │
│ > Advanced Filters (Collapsed by default)          │
│ ┌─────────────────────────────────────────────────┐ │
│ │ Value Range: [Min] [Max]                       │ │
│ │ Limit: [___100___] (Slider: 10-1000)          │ │
│ └────────────────────────────────────────────────┘ │
│                                                     │
│ ┌─ Active Filters ───────────────────────────────┐ │
│ │ [Table: sensor_table ✕] [Device: device_001 ✕]│ │
│ │ [Time: Last 24h ✕] [Measurements: 2 selected ✕]│ │
│ └────────────────────────────────────────────────┘ │
│                                                     │
│ [Apply Filters - Primary]  [Reset - Outline]       │
└─────────────────────────────────────────────────────┘
```

**实现要点**：

1. **分组与折叠**：
```vue
<UCard>
  <template #header>
    <div class="flex items-center justify-between">
      <h3>{{ t('data.filters') }}</h3>
      <UButton variant="ghost" size="sm" @click="toggleExpanded">
        <Icon :name="expanded ? 'i-heroicons-chevron-up' : 'i-heroicons-chevron-down'" />
      </UButton>
    </div>
  </template>

  <!-- Basic Filters - Always Visible -->
  <div class="space-y-6">
    <UFormGroup label="Basic Filters">
      <div class="grid gap-4 sm:grid-cols-1 md:grid-cols-2 lg:grid-cols-3">
        <UFormField :label="t('data.selectTable')" v-if="isTableModel">
          <USelectMenu v-model="selectedTable" :options="tableOptions" />
        </UFormField>
        <UFormField :label="t('data.devices')">
          <USelectMenu v-model="selectedDevices" :options="deviceOptions" 
                       multiple searchable />
        </UFormField>
        <UFormField :label="t('data.measurements')">
          <USelectMenu v-model="selectedMeasurements" 
                       :options="measurementOptions" 
                       multiple searchable />
        </UFormField>
      </div>
    </UFormGroup>

    <!-- Time Range with Quick Select -->
    <UFormGroup label="Time Range">
      <div class="space-y-3">
        <!-- Quick Select Buttons -->
        <div class="flex flex-wrap gap-2">
          <UButton v-for="preset in timePresets" :key="preset.label"
                   size="sm" variant="outline"
                   @click="selectTimePreset(preset)">
            {{ preset.label }}
          </UButton>
        </div>
        <!-- Custom Time Range -->
        <div class="grid gap-4 sm:grid-cols-1 md:grid-cols-2">
          <UFormField :label="t('metadata.startTime')">
            <UInput v-model="startTime" type="datetime-local" />
          </UFormField>
          <UFormField :label="t('metadata.endTime')">
            <UInput v-model="endTime" type="datetime-local" />
          </UFormField>
        </div>
      </div>
    </UFormGroup>

    <!-- Advanced Filters - Collapsible -->
    <UAccordion :items="[{ label: 'Advanced Filters', slot: 'advanced' }]">
      <template #advanced>
        <div class="space-y-4 pt-4">
          <UFormGroup label="Value Range">
            <div class="grid gap-4 sm:grid-cols-1 md:grid-cols-2">
              <UFormField label="Min">
                <UInput v-model.number="valueRange.min" type="number" 
                        placeholder="e.g., 0" />
              </UFormField>
              <UFormField label="Max">
                <UInput v-model.number="valueRange.max" type="number" 
                        placeholder="e.g., 100" />
              </UFormField>
            </div>
          </UFormGroup>
          
          <UFormField label="Page Size">
            <URange v-model="limit" :min="10" :max="1000" :step="10" />
            <div class="text-sm text-gray-500 mt-1">{{ limit }} rows per page</div>
          </UFormField>
        </div>
      </template>
    </UAccordion>

    <!-- Active Filters Preview -->
    <div v-if="activeFilters.length > 0" class="space-y-2">
      <div class="text-sm font-medium text-gray-700 dark:text-gray-300">
        Active Filters
      </div>
      <div class="flex flex-wrap gap-2">
        <UBadge v-for="filter in activeFilters" :key="filter.key"
                color="primary" variant="soft" size="lg">
          {{ filter.label }}
          <button @click="removeFilter(filter.key)" class="ml-1">
            <Icon name="i-heroicons-x-mark" class="w-3 h-3" />
          </button>
        </UBadge>
      </div>
    </div>
  </div>

  <!-- Actions -->
  <template #footer>
    <div class="flex gap-3">
      <UButton color="primary" class="flex-1" size="lg"
               :loading="applying" @click="applyFilters">
        {{ t('data.applyFilters') }}
      </UButton>
      <UButton variant="outline" size="lg" @click="resetFilters">
        {{ t('common.reset') }}
      </UButton>
    </div>
  </template>
</UCard>
```

2. **响应式断点**：
```typescript
// composables/useResponsive.ts
export function useResponsive() {
  const isSmall = useMediaQuery('(max-width: 640px)')
  const isMedium = useMediaQuery('(min-width: 641px) and (max-width: 1024px)')
  const isLarge = useMediaQuery('(min-width: 1025px)')
  
  const filterGridCols = computed(() => {
    if (isSmall.value) return 'grid-cols-1'
    if (isMedium.value) return 'grid-cols-2'
    return 'grid-cols-3'
  })
  
  return { isSmall, isMedium, isLarge, filterGridCols }
}
```

3. **快捷时间范围**：
```typescript
const timePresets = [
  { label: 'Last 1h', value: 3600000 },
  { label: 'Last 6h', value: 21600000 },
  { label: 'Last 24h', value: 86400000 },
  { label: 'Last 7d', value: 604800000 },
  { label: 'Last 30d', value: 2592000000 }
]

function selectTimePreset(preset) {
  const now = Date.now()
  endTime.value = formatDateTime(now)
  startTime.value = formatDateTime(now - preset.value)
  applyFilters()
}
```

4. **活动过滤器预览**：
```typescript
const activeFilters = computed(() => {
  const filters = []
  if (selectedTable.value) {
    filters.push({ key: 'table', label: `Table: ${selectedTable.value}` })
  }
  if (selectedDevices.value.length > 0) {
    filters.push({ 
      key: 'devices', 
      label: `Devices: ${selectedDevices.value.length} selected` 
    })
  }
  if (selectedMeasurements.value.length > 0) {
    filters.push({ 
      key: 'measurements', 
      label: `Measurements: ${selectedMeasurements.value.length} selected` 
    })
  }
  if (startTime.value || endTime.value) {
    filters.push({ key: 'time', label: 'Time range set' })
  }
  return filters
})

function removeFilter(key) {
  switch (key) {
    case 'table':
      selectedTable.value = ''
      break
    case 'devices':
      selectedDevices.value = []
      break
    case 'measurements':
      selectedMeasurements.value = []
      break
    case 'time':
      startTime.value = ''
      endTime.value = ''
      break
  }
  applyFilters()
}
```

**预期改进**：
- ✅ 响应式布局适配所有屏幕尺寸
- ✅ 快捷时间选择提升效率
- ✅ 高级过滤器折叠减少视觉噪音
- ✅ 活动过滤器预览提供清晰反馈
- ✅ 更好的视觉层次和信息架构

---

### 阶段二：ChartVisualizationView 重构（优先级：🔴 高）

#### 目标
将混乱的图表配置界面改造为专业的数据可视化工作台。

#### 设计方案

**新布局结构**：
```
┌───────────────────────────────────────────────────────┐
│ ← Back to Metadata        [Export] [Fullscreen]      │
├───────────────────────────────────────────────────────┤
│ Chart Visualization - sensor_data.tsfile              │
├───────────────────────────────────────────────────────┤
│ ┌─ Configuration ─────────────────────────────────┐   │
│ │                                                  │   │
│ │ Data Selection                                   │   │
│ │ ├─ Measurements: [temperature, humidity ▼]      │   │
│ │ └─ Devices: [All devices ▼]                     │   │
│ │                                                  │   │
│ │ Time Range                                       │   │
│ │ ├─ Presets: [1h] [6h] [24h] [7d] [Custom]      │   │
│ │ └─ Custom: [2024-01-01 00:00] to [Now]         │   │
│ │                                                  │   │
│ │ Aggregation (Optional)                           │   │
│ │ ├─ Type: [None / AVG / MIN / MAX / COUNT ▼]    │   │
│ │ └─ Window: [5 minutes ▼]  (if aggregation set)  │   │
│ │                                                  │   │
│ │ Display Options                                  │   │
│ │ ├─ Max Points: [___1000___] (100-10000)        │   │
│ │ ├─ Line Style: [Smooth / Sharp]                │   │
│ │ └─ Show Data Points: [✓]                        │   │
│ │                                                  │   │
│ │ [Load Chart - Primary, Full Width]              │   │
│ └──────────────────────────────────────────────────┘   │
│                                                         │
│ ┌─ Chart ──────────────────────────────────────────┐   │
│ │                                                   │   │
│ │  [Chart Toolbar: 📊 Download 🔍 Zoom 📷 Save]    │   │
│ │                                                   │   │
│ │         📈 Interactive Line Chart                │   │
│ │            (ECharts Component)                   │   │
│ │                                                   │   │
│ │  Legend: [—temperature] [—humidity] [—pressure]  │   │
│ │  Status: Showing 1,000 of 86,400 points         │   │
│ │          (Downsampled using LTTB)               │   │
│ │                                                   │   │
│ └───────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────┘
```

**实现要点**：

1. **统一配置面板**：
```vue
<UCard class="mb-6">
  <template #header>
    <h3 class="text-lg font-semibold">Configuration</h3>
  </template>

  <div class="space-y-6">
    <!-- Data Selection -->
    <div>
      <h4 class="text-sm font-medium mb-3">Data Selection</h4>
      <div class="grid gap-4 sm:grid-cols-1 lg:grid-cols-2">
        <UFormField label="Measurements" required>
          <USelectMenu v-model="selectedMeasurements" 
                       :options="measurementOptions"
                       multiple searchable 
                       placeholder="Select measurements to visualize" />
        </UFormField>
        <UFormField label="Devices">
          <USelectMenu v-model="selectedDevices"
                       :options="deviceOptions"
                       multiple searchable
                       placeholder="All devices" />
        </UFormField>
      </div>
    </div>

    <!-- Time Range -->
    <div>
      <h4 class="text-sm font-medium mb-3">Time Range</h4>
      <div class="space-y-3">
        <div class="flex flex-wrap gap-2">
          <UButton v-for="preset in timePresets" :key="preset.label"
                   size="sm" 
                   :variant="isPresetActive(preset) ? 'solid' : 'outline'"
                   @click="selectTimePreset(preset)">
            {{ preset.label }}
          </UButton>
        </div>
        <div class="grid gap-4 sm:grid-cols-1 md:grid-cols-2">
          <UFormField label="Start Time">
            <UInput v-model="startTime" type="datetime-local" />
          </UFormField>
          <UFormField label="End Time">
            <UInput v-model="endTime" type="datetime-local" />
          </UFormField>
        </div>
      </div>
    </div>

    <!-- Aggregation -->
    <div>
      <h4 class="text-sm font-medium mb-3">
        Aggregation 
        <span class="text-xs text-gray-500">(Optional - reduces data points)</span>
      </h4>
      <div class="grid gap-4 sm:grid-cols-1 md:grid-cols-2">
        <UFormField label="Type">
          <USelect v-model="aggregationType" :options="aggregationOptions" />
        </UFormField>
        <UFormField v-if="aggregationType" label="Window Size">
          <USelect v-model="windowSize" :options="windowSizeOptions" />
        </UFormField>
      </div>
    </div>

    <!-- Display Options -->
    <UAccordion :items="[{ label: 'Display Options', slot: 'display' }]">
      <template #display>
        <div class="space-y-4 pt-4">
          <UFormField label="Max Points">
            <URange v-model="maxPoints" :min="100" :max="10000" :step="100" />
            <div class="text-sm text-gray-500 mt-1">
              {{ maxPoints }} points (auto-downsampling if needed)
            </div>
          </UFormField>
          
          <div class="grid gap-4 sm:grid-cols-1 md:grid-cols-2">
            <UFormField label="Line Style">
              <USelect v-model="lineStyle" 
                       :options="[{label: 'Smooth', value: true}, 
                                  {label: 'Sharp', value: false}]" />
            </UFormField>
            <UFormField label="Show Data Points">
              <UToggle v-model="showDataPoints" />
            </UFormField>
          </div>
        </div>
      </template>
    </UAccordion>
  </div>

  <template #footer>
    <UButton color="primary" size="lg" block
             :loading="loading" :disabled="!canLoadChart"
             @click="loadChartData">
      <Icon name="i-heroicons-chart-bar" class="mr-2" />
      Load Chart
    </UButton>
  </template>
</UCard>
```

2. **增强的ChartPanel**：
```vue
<!-- components/ChartPanel.vue -->
<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">Chart</h3>
        <div class="flex gap-2">
          <UButton size="sm" variant="ghost" icon="i-heroicons-arrow-down-tray"
                   @click="downloadChart('png')">
            PNG
          </UButton>
          <UButton size="sm" variant="ghost" icon="i-heroicons-arrow-down-tray"
                   @click="downloadChart('svg')">
            SVG
          </UButton>
          <UButton size="sm" variant="ghost" icon="i-heroicons-arrow-path"
                   @click="$emit('reload')">
            Refresh
          </UButton>
          <UButton size="sm" variant="ghost" icon="i-heroicons-arrows-pointing-out"
                   @click="toggleFullscreen">
            Fullscreen
          </UButton>
        </div>
      </div>
    </template>

    <div v-if="loading" class="flex items-center justify-center py-20">
      <div class="text-center space-y-3">
        <div class="w-12 h-12 border-4 border-primary border-t-transparent 
                    rounded-full animate-spin mx-auto"></div>
        <p class="text-sm text-gray-500">Loading chart data...</p>
      </div>
    </div>

    <UAlert v-else-if="error" color="error" 
            :title="t('error.chartLoadFailed')" 
            :description="error" />

    <div v-else-if="!chartData" class="py-20 text-center">
      <div class="space-y-3">
        <Icon name="i-heroicons-chart-bar" class="w-16 h-16 text-gray-300 mx-auto" />
        <p class="text-gray-500">Configure and load chart to visualize data</p>
      </div>
    </div>

    <div v-else>
      <!-- ECharts Container -->
      <div ref="chartContainer" class="w-full" style="height: 500px"></div>

      <!-- Chart Info -->
      <div class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
        <div class="flex items-center justify-between text-sm">
          <div class="flex items-center gap-4">
            <div class="text-gray-600 dark:text-gray-400">
              Showing {{ formatNumber(chartData.returnedPoints) }} of 
              {{ formatNumber(chartData.originalPoints) }} points
            </div>
            <UBadge v-if="chartData.downsampled" color="blue" variant="soft">
              Downsampled (LTTB)
            </UBadge>
            <UBadge v-if="chartData.aggregated" color="green" variant="soft">
              Aggregated ({{ chartData.aggregation }})
            </UBadge>
          </div>
          <div class="text-gray-500">
            {{ formatTimeRange(chartData.timeRange) }}
          </div>
        </div>
      </div>
    </div>
  </UCard>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'

const props = defineProps<{
  chartData: ChartData | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{
  reload: []
  pointClick: [data: any]
}>()

const chartContainer = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

onMounted(() => {
  if (chartContainer.value) {
    chartInstance = echarts.init(chartContainer.value)
    chartInstance.on('click', (params) => {
      emit('pointClick', params)
    })
  }
})

watch(() => props.chartData, (newData) => {
  if (newData && chartInstance) {
    updateChart(newData)
  }
})

function updateChart(data: ChartData) {
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: data.series.map(s => s.name),
      bottom: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'time',
      boundaryGap: false
    },
    yAxis: {
      type: 'value'
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      },
      {
        start: 0,
        end: 100
      }
    ],
    series: data.series.map(s => ({
      name: s.name,
      type: 'line',
      data: s.data.map(d => [d.timestamp, d.value]),
      smooth: true,
      showSymbol: false,
      emphasis: {
        focus: 'series'
      }
    }))
  }
  
  chartInstance?.setOption(option)
}

function downloadChart(format: 'png' | 'svg') {
  if (!chartInstance) return
  
  const url = chartInstance.getDataURL({
    type: format,
    pixelRatio: 2,
    backgroundColor: '#fff'
  })
  
  const link = document.createElement('a')
  link.href = url
  link.download = `chart-${Date.now()}.${format}`
  link.click()
}

function toggleFullscreen() {
  if (!chartContainer.value) return
  
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    chartContainer.value.requestFullscreen()
  }
}

onBeforeUnmount(() => {
  chartInstance?.dispose()
})
</script>
```

**预期改进**：
- ✅ 配置面板逻辑分组，易于理解
- ✅ 时间范围快捷选择提升效率
- ✅ 高级选项折叠减少视觉噪音
- ✅ 图表工具栏提供专业功能（下载、全屏、刷新）
- ✅ 图表信息显示清晰（点数、降采样状态、聚合状态）
- ✅ 更好的空状态和加载状态

---

### 阶段三：DataTable 增强（优先级：🟡 中）

#### 目标
提升数据表格的功能性和用户体验。

#### 改进点

1. **列管理**：
```vue
<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3>{{ t('data.title') }}</h3>
        <div class="flex gap-2">
          <!-- Column Visibility -->
          <UDropdown :items="columnVisibilityItems">
            <UButton variant="outline" size="sm" 
                     icon="i-heroicons-view-columns">
              Columns
            </UButton>
          </UDropdown>
          
          <!-- Export -->
          <UButton variant="outline" size="sm" 
                   icon="i-heroicons-arrow-down-tray"
                   @click="showExportDialog = true">
            Export
          </UButton>
        </div>
      </div>
    </template>

    <!-- Enhanced Table -->
    <UTable :data="tableData" :columns="visibleColumns" 
            :loading="loading"
            :sort="sort"
            @update:sort="handleSort"
            class="max-h-[600px] overflow-auto">
      
      <!-- Custom Cell Renderers -->
      <template #timestamp-cell="{ row }">
        <div class="flex items-center gap-2">
          <Icon name="i-heroicons-clock" class="w-4 h-4 text-gray-400" />
          <span class="text-sm font-mono">
            {{ formatTimestamp(row.original.timestamp) }}
          </span>
        </div>
      </template>

      <template #device-cell="{ row }">
        <div class="flex items-center gap-2">
          <Icon name="i-heroicons-cpu-chip" class="w-4 h-4 text-gray-400" />
          <span class="text-sm font-medium">
            {{ row.original.device }}
          </span>
        </div>
      </template>

      <!-- Numeric values with formatting -->
      <template #[`${col}-cell`]="{ row }" 
                v-for="col in numericColumns" :key="col">
        <span class="text-sm font-mono">
          {{ formatNumber(row.original.measurements[col]) }}
        </span>
      </template>
    </UTable>

    <!-- Enhanced Pagination -->
    <template #footer>
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div class="text-sm text-gray-600 dark:text-gray-400">
            {{ t('data.showing') }} 
            <span class="font-medium">{{ offset + 1 }}</span> - 
            <span class="font-medium">{{ offset + data.length }}</span> of 
            <span class="font-medium">{{ formatNumber(total) }}</span>
            <span v-if="hasMore" class="ml-2">({{ t('data.moreAvailable') }})</span>
          </div>
          
          <USelectMenu v-model="internalLimit" 
                       :options="limitOptions"
                       size="sm" class="w-32">
            <template #label>
              <span class="text-sm">{{ internalLimit }} / page</span>
            </template>
          </USelectMenu>
        </div>

        <div class="flex gap-2">
          <UButton :disabled="offset === 0" 
                   variant="outline" size="sm"
                   icon="i-heroicons-chevron-double-left"
                   @click="$emit('page-change', 'first')">
            First
          </UButton>
          <UButton :disabled="offset === 0"
                   variant="outline" size="sm"
                   icon="i-heroicons-chevron-left"
                   @click="$emit('page-change', 'prev')">
            {{ t('common.previous') }}
          </UButton>
          <UButton :disabled="!hasMore"
                   variant="outline" size="sm"
                   trailing-icon="i-heroicons-chevron-right"
                   @click="$emit('page-change', 'next')">
            {{ t('common.next') }}
          </UButton>
          <UButton :disabled="!hasMore"
                   variant="outline" size="sm"
                   trailing-icon="i-heroicons-chevron-double-right"
                   @click="$emit('page-change', 'last')">
            Last
          </UButton>
        </div>
      </div>
    </template>
  </UCard>

  <!-- Export Dialog -->
  <UModal v-model="showExportDialog">
    <UCard>
      <template #header>
        <h3>Export Data</h3>
      </template>

      <div class="space-y-4">
        <UFormField label="Format">
          <USelectMenu v-model="exportFormat" 
                       :options="[
                         { label: 'CSV', value: 'csv' },
                         { label: 'JSON', value: 'json' },
                         { label: 'Excel (XLSX)', value: 'xlsx' }
                       ]" />
        </UFormField>

        <UFormField label="Scope">
          <USelectMenu v-model="exportScope"
                       :options="[
                         { label: 'Current page', value: 'page' },
                         { label: 'All filtered data', value: 'all' }
                       ]" />
        </UFormField>

        <UFormField label="Columns">
          <UCheckbox v-model="exportAllColumns" 
                     label="Export all columns" />
        </UFormField>
      </div>

      <template #footer>
        <div class="flex gap-3 justify-end">
          <UButton variant="outline" @click="showExportDialog = false">
            Cancel
          </UButton>
          <UButton color="primary" :loading="exporting"
                   @click="performExport">
            Export
          </UButton>
        </div>
      </template>
    </UCard>
  </UModal>
</template>

<script setup lang="ts">
const visibleColumns = ref<string[]>([])
const sort = ref({ column: 'timestamp', direction: 'asc' })
const showExportDialog = ref(false)
const exportFormat = ref('csv')
const exportScope = ref('page')
const exportAllColumns = ref(true)
const exporting = ref(false)

const columnVisibilityItems = computed(() => {
  return props.columnNames.map(col => ({
    label: col,
    icon: visibleColumns.value.includes(col) 
      ? 'i-heroicons-check-circle' 
      : 'i-heroicons-circle',
    click: () => toggleColumn(col)
  }))
})

const numericColumns = computed(() => {
  // Detect numeric columns from data
  if (props.data.length === 0) return []
  const firstRow = props.data[0]
  return Object.keys(firstRow.measurements).filter(key => 
    typeof firstRow.measurements[key] === 'number'
  )
})

function toggleColumn(column: string) {
  const index = visibleColumns.value.indexOf(column)
  if (index > -1) {
    visibleColumns.value.splice(index, 1)
  } else {
    visibleColumns.value.push(column)
  }
}

function handleSort(sortConfig) {
  sort.value = sortConfig
  emit('sort-change', sortConfig)
}

async function performExport() {
  exporting.value = true
  try {
    // Export logic...
    await new Promise(resolve => setTimeout(resolve, 1000))
    showToast('Export completed', 'success')
    showExportDialog.value = false
  } catch (error) {
    showToast('Export failed', 'error')
  } finally {
    exporting.value = false
  }
}
</script>
```

**新功能**：
- ✅ 列可见性控制
- ✅ 列排序（前端或后端）
- ✅ 增强的导出对话框（格式、范围选择）
- ✅ 更好的分页控件（首页、末页）
- ✅ 数字列自动格式化
- ✅ 图标提升视觉体验

2. **虚拟滚动（大数据集）**：
```vue
<!-- 使用 @tanstack/vue-virtual -->
<script setup lang="ts">
import { useVirtualizer } from '@tanstack/vue-virtual'

const tableContainer = ref<HTMLElement>()

const virtualizer = useVirtualizer({
  count: props.data.length,
  getScrollElement: () => tableContainer.value,
  estimateSize: () => 48, // Row height
  overscan: 5
})

const virtualRows = computed(() => virtualizer.value.getVirtualItems())
</script>

<template>
  <div ref="tableContainer" class="h-[600px] overflow-auto">
    <div :style="{ height: `${virtualizer.getTotalSize()}px` }" 
         class="relative">
      <div v-for="virtualRow in virtualRows" :key="virtualRow.index"
           :style="{
             position: 'absolute',
             top: 0,
             left: 0,
             width: '100%',
             height: `${virtualRow.size}px`,
             transform: `translateY(${virtualRow.start}px)`
           }">
        <!-- Row content -->
        <DataTableRow :data="data[virtualRow.index]" />
      </div>
    </div>
  </div>
</template>
```

---

### 阶段四：设计系统建立（优先级：🟡 中）

#### 目标
建立统一的设计令牌和组件样式指南。

#### Design Tokens

**创建设计令牌文件**：
```typescript
// composables/useDesignTokens.ts
export const designTokens = {
  spacing: {
    xs: '0.25rem',  // 4px
    sm: '0.5rem',   // 8px
    md: '1rem',     // 16px
    lg: '1.5rem',   // 24px
    xl: '2rem',     // 32px
    '2xl': '3rem'   // 48px
  },
  
  fontSize: {
    xs: '0.75rem',   // 12px
    sm: '0.875rem',  // 14px
    base: '1rem',    // 16px
    lg: '1.125rem',  // 18px
    xl: '1.25rem',   // 20px
    '2xl': '1.5rem', // 24px
    '3xl': '1.875rem' // 30px
  },
  
  borderRadius: {
    sm: '0.25rem',  // 4px
    md: '0.5rem',   // 8px
    lg: '0.75rem',  // 12px
    xl: '1rem'      // 16px
  },
  
  colors: {
    primary: {
      50: '#eff6ff',
      500: '#3b82f6',
      600: '#2563eb',
      700: '#1d4ed8'
    },
    success: {
      500: '#10b981',
      600: '#059669'
    },
    warning: {
      500: '#f59e0b',
      600: '#d97706'
    },
    error: {
      500: '#ef4444',
      600: '#dc2626'
    }
  },
  
  shadows: {
    sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
    md: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
    lg: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
    xl: '0 20px 25px -5px rgb(0 0 0 / 0.1)'
  },
  
  animation: {
    duration: {
      fast: '150ms',
      normal: '250ms',
      slow: '350ms'
    },
    easing: {
      ease: 'cubic-bezier(0.4, 0, 0.2, 1)',
      easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
      easeOut: 'cubic-bezier(0, 0, 0.2, 1)'
    }
  }
}

export function useDesignTokens() {
  return designTokens
}
```

**Tailwind配置**：
```javascript
// tailwind.config.js
export default {
  theme: {
    extend: {
      spacing: {
        '18': '4.5rem',
        '88': '22rem'
      },
      colors: {
        primary: {
          50: '#eff6ff',
          // ... 完整色板
        }
      },
      animation: {
        'fade-in': 'fadeIn 250ms ease-out',
        'slide-in': 'slideIn 250ms ease-out'
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' }
        },
        slideIn: {
          '0%': { transform: 'translateY(-10px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' }
        }
      }
    }
  }
}
```

#### 组件样式指南

**统一的Card样式**：
```vue
<!-- All UCard instances should follow this pattern -->
<UCard class="shadow-md hover:shadow-lg transition-shadow">
  <template #header>
    <div class="flex items-center justify-between">
      <h3 class="text-lg font-semibold">{{ title }}</h3>
      <slot name="header-actions" />
    </div>
  </template>
  
  <div class="space-y-4">
    <slot />
  </div>
  
  <template #footer v-if="$slots.footer">
    <div class="flex justify-end gap-3">
      <slot name="footer" />
    </div>
  </template>
</UCard>
```

**统一的Button尺寸**：
```typescript
// Primary actions: size="lg"
<UButton size="lg" color="primary">Submit</UButton>

// Secondary actions: size="md" (default)
<UButton>Cancel</UButton>

// Tertiary/Icon buttons: size="sm"
<UButton size="sm" variant="ghost" icon="i-heroicons-x-mark" />
```

**统一的间距**：
```vue
<!-- Section spacing -->
<div class="space-y-6">  <!-- Between major sections -->
  <div class="space-y-4">  <!-- Within a section -->
    <div class="space-y-2">  <!-- Within a subsection -->
      <!-- Content -->
    </div>
  </div>
</div>

<!-- Grid gaps -->
<div class="grid gap-4">  <!-- Standard grid -->
<div class="flex gap-3">  <!-- Flex containers -->
```

---

### 阶段五：响应式和可访问性（优先级：🟢 低）

#### 响应式断点策略

```typescript
// composables/useBreakpoints.ts
export function useBreakpoints() {
  const breakpoints = {
    sm: 640,
    md: 768,
    lg: 1024,
    xl: 1280,
    '2xl': 1536
  }
  
  const isSmall = useMediaQuery(`(max-width: ${breakpoints.sm - 1}px)`)
  const isMedium = useMediaQuery(
    `(min-width: ${breakpoints.sm}px) and (max-width: ${breakpoints.lg - 1}px)`
  )
  const isLarge = useMediaQuery(`(min-width: ${breakpoints.lg}px)`)
  
  return {
    isSmall,
    isMedium,
    isLarge,
    breakpoints
  }
}
```

**应用响应式**：
```vue
<template>
  <div :class="containerClass">
    <!-- Layout adapts based on screen size -->
  </div>
</template>

<script setup>
const { isSmall, isMedium, isLarge } = useBreakpoints()

const containerClass = computed(() => ({
  'p-4': isSmall.value,
  'p-6': isMedium.value,
  'p-8': isLarge.value,
  'max-w-7xl mx-auto': isLarge.value
}))
</script>
```

#### 可访问性改进

1. **键盘导航**：
```vue
<UButton @click="action" @keydown.enter="action" @keydown.space="action">
  Action
</UButton>
```

2. **ARIA属性**：
```vue
<div role="region" aria-label="Data filters">
  <UFormField label="Devices" :aria-describedby="'devices-help'">
    <USelectMenu aria-label="Select devices" />
  </UFormField>
  <p id="devices-help" class="text-sm text-gray-500">
    Select one or more devices to filter data
  </p>
</div>
```

3. **焦点管理**：
```typescript
// When modal opens, focus first input
watch(() => props.open, (isOpen) => {
  if (isOpen) {
    nextTick(() => {
      firstInputRef.value?.focus()
    })
  }
})
```

4. **颜色对比度**：
确保所有文字与背景的对比度至少达到WCAG AA标准（4.5:1）。

---

## 实施路线图

### 第1周：FilterPanel重构
- [ ] 设计新的FilterPanel布局
- [ ] 实现响应式网格
- [ ] 添加快捷时间选择
- [ ] 实现高级过滤器折叠
- [ ] 添加活动过滤器预览
- [ ] 测试并调优

### 第2周：ChartVisualizationView重构
- [ ] 重新设计配置面板布局
- [ ] 统一配置Card
- [ ] 增强ChartPanel（工具栏、下载、全屏）
- [ ] 添加图表信息显示
- [ ] 改进空状态和加载状态
- [ ] 测试并调优

### 第3周：DataTable增强
- [ ] 添加列可见性控制
- [ ] 实现列排序
- [ ] 增强导出功能（对话框、格式选择）
- [ ] 改进分页控件
- [ ] 添加虚拟滚动（可选）
- [ ] 测试并调优

### 第4周：设计系统和文档
- [ ] 建立design tokens
- [ ] 更新Tailwind配置
- [ ] 创建组件样式指南文档
- [ ] 统一现有组件样式
- [ ] 添加响应式和可访问性改进
- [ ] 最终测试和bug修复

---

## 技术栈优化

### 建议添加的依赖

```json
{
  "dependencies": {
    "@tanstack/vue-virtual": "^3.0.0",  // 虚拟滚动
    "@vueuse/core": "^10.0.0",           // 实用工具（已有）
    "echarts": "^5.4.0",                 // 图表（已有）
    "date-fns": "^3.0.0"                 // 日期格式化
  },
  "devDependencies": {
    "@nuxt/test-utils": "^3.0.0",       // 测试工具
    "vitest": "^1.0.0",                  // 单元测试
    "playwright": "^1.40.0"              // E2E测试
  }
}
```

### 性能优化建议

1. **懒加载路由**：
```typescript
const routes = [
  {
    path: '/chart/:fileId',
    component: () => import('@/views/ChartVisualizationView.vue')
  }
]
```

2. **组件懒加载**：
```vue
<script setup>
const HeavyChart = defineAsyncComponent(() => 
  import('@/components/HeavyChartComponent.vue')
)
</script>
```

3. **防抖输入**：
```typescript
import { useDebounceFn } from '@vueuse/core'

const debouncedSearch = useDebounceFn((query) => {
  performSearch(query)
}, 300)
```

4. **memo化计算**：
```typescript
const expensiveComputation = computed(() => {
  // Automatically memoized
  return heavyCalculation(props.data)
})
```

---

## 测试策略

### 单元测试
```typescript
// FilterPanel.test.ts
import { mount } from '@vue/test-utils'
import FilterPanel from '@/components/FilterPanel.vue'

describe('FilterPanel', () => {
  it('should render basic filters', () => {
    const wrapper = mount(FilterPanel, {
      props: { fileId: 'test123' }
    })
    expect(wrapper.find('[data-test="device-select"]').exists()).toBe(true)
  })
  
  it('should apply filters when button clicked', async () => {
    const wrapper = mount(FilterPanel)
    await wrapper.find('[data-test="apply-button"]').trigger('click')
    expect(wrapper.emitted('change')).toBeTruthy()
  })
})
```

### E2E测试
```typescript
// filter.spec.ts
import { test, expect } from '@playwright/test'

test('user can filter data', async ({ page }) => {
  await page.goto('/data/test-file-id')
  
  // Select device
  await page.click('[data-test="device-select"]')
  await page.click('text=device_001')
  
  // Apply filters
  await page.click('[data-test="apply-button"]')
  
  // Verify results
  await expect(page.locator('[data-test="data-table"]')).toBeVisible()
  await expect(page.locator('text=device_001')).toBeVisible()
})
```

---

## 总结

### 预期收益

**用户体验**：
- ⬆️ 50% 过滤效率提升（快捷选择、更好的布局）
- ⬆️ 40% 图表配置效率提升（统一面板、逻辑分组）
- ⬆️ 60% 移动端可用性提升（响应式设计）

**代码质量**：
- ⬆️ 30% 代码可维护性提升（设计系统、一致性）
- ⬆️ 组件复用率提升（通用组件、composables）
- ⬇️ 50% 样式重复（design tokens）

**性能**：
- ⬆️ 虚拟滚动支持10,000+行无卡顿
- ⬇️ 30% 首屏加载时间（代码分割、懒加载）
- ⬇️ 50% 重渲染次数（优化响应式）

### 关键成功因素

1. **渐进式改进**：按阶段实施，每周交付可用功能
2. **用户反馈**：每个阶段收集反馈，及时调整
3. **测试覆盖**：确保改造不引入新bug
4. **文档同步**：同步更新组件文档和使用指南

### 长期维护

- 建立设计审查流程，确保新组件遵循设计系统
- 定期更新依赖，保持技术栈现代化
- 持续性能监控，识别并优化瓶颈
- 收集用户反馈，持续改进用户体验

---

**文档版本**: 1.0  
**创建日期**: 2024-01-29  
**最后更新**: 2024-01-29  
**状态**: Draft - 待审核

如有问题或建议，请联系开发团队。
