# Frontend Enhancement Plan (前端增强计划)

本文档描述了为支持后端新增的多表、多设备和增强分页功能所需的前端修改计划。

## 概述

后端已经实现了以下增强功能：
1. **获取表列表 API**: `GET /api/tables/{fileId}` - 获取 TsFile 中所有表的信息
2. **获取设备列表 API**: `GET /api/tables/{fileId}/devices` - 获取所有唯一设备标识
3. **表数据查询 API**: `POST /api/tables/query` - 从特定表查询数据，支持高级分页
4. **Tree Model 支持**: 后端自动检测并支持 V3/Legacy Tree Model TsFile 格式

## Tree Model 支持 (新增)

### 后端实现

后端 `TsFileDataReader` 已实现自动检测和处理两种 TsFile 模型：

- **Table Model (V4)**: 使用 `ITsFileReader.query()` API，支持 TAG/FIELD 列
- **Tree Model (V3)**: 使用 `TsFileSequenceReader` + `QueryExpression` API

检测逻辑：
```java
// 如果没有 TableSchema，则为 Tree Model
if (schemas.isEmpty()) {
  return readTreeModelData(filePath, effStart, effEnd, limit, offset);
}
```

### 前端适配要点

1. **无需修改 API 调用**: 现有的 `/api/data/preview` 和 `/api/data/query` 接口对两种模型透明
2. **设备标识显示**: Tree Model 的设备标识为完整路径（如 `root.device1`），Table Model 为 `tablename.tag1.tag2`
3. **元数据展示**: Tree Model 文件可能没有 `tables` 字段，UI 需要优雅处理
4. **列类型**: Tree Model 没有 TAG/FIELD 区分，所有列都是测量值列

---

## 1. API 客户端更新

### 文件: `frontend/src/api/types.ts`

添加新的类型定义：

```typescript
// 表信息
export interface TableInfo {
  tableName: string
  columns: string[]
  tagColumns: string[]
  fieldColumns: string[]
  rowCount: number
}

// 表列表响应
export interface TableListResponse {
  tables: TableInfo[]
  totalCount: number
}

// 设备信息
export interface DeviceInfo {
  deviceId: string
  tableName: string
  tagValues: string[]
  dataPointCount: number
}

// 设备列表响应
export interface DeviceListResponse {
  devices: DeviceInfo[]
  totalCount: number
}

// 表数据请求
export interface TableDataRequest {
  fileId: string
  tableName: string
  startTime?: number
  endTime?: number
  columns?: string[]
  valueRange?: ValueRange
  limit: number
  offset: number
}

// 表数据响应
export interface TableDataResponse {
  tableName: string
  columns: string[]
  columnTypes: string[]
  rows: Record<string, unknown>[]
  total: number
  limit: number
  offset: number
  hasMore: boolean
}
```

### 文件: `frontend/src/api/index.ts`

添加新的 API 函数：

```typescript
// 获取表列表
export async function getTableList(fileId: string): Promise<TableListResponse> {
  const response = await apiClient.get(`/tables/${fileId}`)
  return response.data
}

// 获取设备列表
export async function getDeviceList(
  fileId: string, 
  tableName?: string
): Promise<DeviceListResponse> {
  const params = tableName ? { tableName } : {}
  const response = await apiClient.get(`/tables/${fileId}/devices`, { params })
  return response.data
}

// 查询表数据
export async function queryTableData(
  request: TableDataRequest
): Promise<TableDataResponse> {
  const response = await apiClient.post('/tables/query', request)
  return response.data
}
```

---

## 2. 状态管理更新

### 文件: `frontend/src/stores/table.ts` (新建)

创建新的 Pinia store 用于管理表相关状态：

```typescript
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { TableInfo, DeviceInfo } from '@/api/types'

export const useTableStore = defineStore('table', () => {
  // 当前选中的表
  const selectedTable = ref<string | null>(null)
  
  // 表列表缓存
  const tableListCache = ref<Map<string, TableInfo[]>>(new Map())
  
  // 设备列表缓存
  const deviceListCache = ref<Map<string, DeviceInfo[]>>(new Map())
  
  // 当前文件的表列表
  const currentTables = computed(() => {
    // 从 fileStore 获取当前 fileId
    const fileStore = useFileStore()
    const fileId = fileStore.currentFileId
    if (!fileId) return []
    return tableListCache.value.get(fileId) || []
  })
  
  // 设置选中的表
  function setSelectedTable(tableName: string | null) {
    selectedTable.value = tableName
  }
  
  // 缓存表列表
  function cacheTableList(fileId: string, tables: TableInfo[]) {
    tableListCache.value.set(fileId, tables)
  }
  
  // 缓存设备列表
  function cacheDeviceList(fileId: string, tableName: string, devices: DeviceInfo[]) {
    const key = `${fileId}:${tableName}`
    deviceListCache.value.set(key, devices)
  }
  
  // 清除缓存
  function clearCache() {
    tableListCache.value.clear()
    deviceListCache.value.clear()
    selectedTable.value = null
  }
  
  return {
    selectedTable,
    currentTables,
    setSelectedTable,
    cacheTableList,
    cacheDeviceList,
    clearCache,
  }
})
```

---

## 3. 组件更新

### 3.1 表选择器组件 (新建)

**文件**: `frontend/src/components/TableSelector.vue`

```vue
<template>
  <UCard>
    <template #header>
      <div class="flex items-center gap-2">
        <UIcon name="i-heroicons-table-cells" />
        <span>{{ $t('table.selectTable') }}</span>
      </div>
    </template>
    
    <USelect
      v-model="selectedTable"
      :options="tableOptions"
      :placeholder="$t('table.selectPlaceholder')"
      :loading="loading"
    />
    
    <!-- 显示选中表的信息 -->
    <div v-if="selectedTableInfo" class="mt-4 text-sm text-gray-600">
      <p>{{ $t('table.columns') }}: {{ selectedTableInfo.columns.length }}</p>
      <p>{{ $t('table.tagColumns') }}: {{ selectedTableInfo.tagColumns.join(', ') || '-' }}</p>
      <p>{{ $t('table.fieldColumns') }}: {{ selectedTableInfo.fieldColumns.join(', ') }}</p>
      <p>{{ $t('table.rowCount') }}: {{ selectedTableInfo.rowCount.toLocaleString() }}</p>
    </div>
  </UCard>
</template>
```

### 3.2 设备选择器组件 (新建)

**文件**: `frontend/src/components/DeviceSelector.vue`

```vue
<template>
  <UCard>
    <template #header>
      <div class="flex items-center gap-2">
        <UIcon name="i-heroicons-cpu-chip" />
        <span>{{ $t('device.selectDevices') }}</span>
      </div>
    </template>
    
    <!-- 多选设备列表 -->
    <div class="space-y-2 max-h-60 overflow-y-auto">
      <UCheckbox
        v-for="device in devices"
        :key="device.deviceId"
        v-model="selectedDevices"
        :value="device.deviceId"
        :label="device.deviceId"
      >
        <template #label>
          <div class="flex justify-between items-center w-full">
            <span>{{ device.deviceId }}</span>
            <span class="text-xs text-gray-500">
              {{ device.dataPointCount.toLocaleString() }} {{ $t('common.points') }}
            </span>
          </div>
        </template>
      </UCheckbox>
    </div>
    
    <!-- 全选/取消全选 -->
    <template #footer>
      <div class="flex gap-2">
        <UButton size="xs" variant="ghost" @click="selectAll">
          {{ $t('common.selectAll') }}
        </UButton>
        <UButton size="xs" variant="ghost" @click="deselectAll">
          {{ $t('common.deselectAll') }}
        </UButton>
      </div>
    </template>
  </UCard>
</template>
```

### 3.3 增强的分页控件组件

**文件**: `frontend/src/components/EnhancedPagination.vue`

```vue
<template>
  <div class="flex items-center justify-between">
    <!-- 分页信息 -->
    <div class="text-sm text-gray-600">
      {{ $t('pagination.showing') }} 
      {{ offset + 1 }} - {{ Math.min(offset + limit, total) }} 
      {{ $t('pagination.of') }} 
      {{ total.toLocaleString() }}
    </div>
    
    <!-- 每页行数选择 -->
    <div class="flex items-center gap-4">
      <USelect
        v-model="pageSize"
        :options="pageSizeOptions"
        size="sm"
      />
      
      <!-- 分页按钮 -->
      <UPagination
        v-model="currentPage"
        :total="totalPages"
        :ui="{ wrapper: 'gap-1' }"
      />
      
      <!-- 跳转到指定页 -->
      <div class="flex items-center gap-2">
        <span class="text-sm">{{ $t('pagination.goTo') }}</span>
        <UInput
          v-model.number="goToPage"
          type="number"
          size="sm"
          class="w-16"
          @keyup.enter="handleGoTo"
        />
      </div>
    </div>
  </div>
</template>
```

---

## 4. 视图页面更新

### 4.1 DataPreviewView.vue 更新

在现有的数据预览页面添加表选择和设备过滤功能：

```vue
<!-- 添加到模板 -->
<div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
  <!-- 表选择器 -->
  <TableSelector 
    v-model="selectedTable"
    :file-id="fileId"
    @change="handleTableChange"
  />
  
  <!-- 设备选择器 -->
  <DeviceSelector
    v-model="selectedDevices"
    :file-id="fileId"
    :table-name="selectedTable"
    @change="handleDevicesChange"
  />
  
  <!-- 现有的过滤器 -->
  <FilterPanel ... />
</div>

<!-- 增强的分页 -->
<EnhancedPagination
  v-model:offset="offset"
  v-model:limit="limit"
  :total="totalRows"
  @change="handlePaginationChange"
/>
```

### 4.2 MetadataView.vue 更新

添加表信息卡片显示：

```vue
<!-- 添加表信息展示区域 -->
<UCard v-if="metadata?.tables?.length">
  <template #header>
    <div class="flex items-center gap-2">
      <UIcon name="i-heroicons-table-cells" class="text-lg" />
      <span class="font-medium">{{ $t('metadata.tables') }}</span>
      <UBadge>{{ metadata.tables.length }}</UBadge>
    </div>
  </template>
  
  <UTable
    :columns="tableColumns"
    :rows="metadata.tables"
  >
    <template #cell-columns="{ row }">
      <div class="flex flex-wrap gap-1">
        <UBadge 
          v-for="col in row.tagColumns" 
          :key="col" 
          color="blue" 
          size="xs"
        >
          TAG: {{ col }}
        </UBadge>
        <UBadge 
          v-for="col in row.fieldColumns" 
          :key="col" 
          color="green" 
          size="xs"
        >
          {{ col }}
        </UBadge>
      </div>
    </template>
  </UTable>
</UCard>
```

---

## 5. 国际化更新

### 文件: `frontend/src/i18n/locales/zh-CN.json`

添加新的翻译键：

```json
{
  "table": {
    "selectTable": "选择表",
    "selectPlaceholder": "请选择一个表",
    "columns": "列数",
    "tagColumns": "标签列",
    "fieldColumns": "数据列",
    "rowCount": "行数",
    "noTables": "此文件不包含表"
  },
  "device": {
    "selectDevices": "选择设备",
    "noDevices": "未找到设备",
    "allDevices": "所有设备"
  },
  "pagination": {
    "showing": "显示",
    "of": "共",
    "goTo": "跳转到",
    "pageSize": "每页行数"
  },
  "common": {
    "selectAll": "全选",
    "deselectAll": "取消全选",
    "points": "数据点"
  },
  "metadata": {
    "tables": "表结构"
  }
}
```

### 文件: `frontend/src/i18n/locales/en-US.json`

```json
{
  "table": {
    "selectTable": "Select Table",
    "selectPlaceholder": "Please select a table",
    "columns": "Columns",
    "tagColumns": "Tag Columns",
    "fieldColumns": "Field Columns",
    "rowCount": "Row Count",
    "noTables": "This file contains no tables"
  },
  "device": {
    "selectDevices": "Select Devices",
    "noDevices": "No devices found",
    "allDevices": "All Devices"
  },
  "pagination": {
    "showing": "Showing",
    "of": "of",
    "goTo": "Go to",
    "pageSize": "Page size"
  },
  "common": {
    "selectAll": "Select All",
    "deselectAll": "Deselect All",
    "points": "points"
  },
  "metadata": {
    "tables": "Table Schema"
  }
}
```

---

## 6. 实现顺序建议

1. **第一阶段**: API 层更新
   - 更新 `types.ts` 添加类型定义
   - 更新 `index.ts` 添加 API 函数

2. **第二阶段**: 状态管理
   - 创建 `tableStore`
   - 更新 `filterStore` 支持表级别过滤

3. **第三阶段**: 基础组件
   - 实现 `TableSelector.vue`
   - 实现 `DeviceSelector.vue`
   - 实现 `EnhancedPagination.vue`

4. **第四阶段**: 页面集成
   - 更新 `DataPreviewView.vue`
   - 更新 `MetadataView.vue`
   - 更新 `ChartVisualizationView.vue`

5. **第五阶段**: 国际化和测试
   - 更新翻译文件
   - 添加单元测试
   - 端到端测试

---

## 7. 注意事项

- 保持与现有代码风格一致
- 使用 shadcn-vue 组件库（项目已从 Nuxt UI 迁移）
- 确保响应式设计
- 添加适当的加载状态和错误处理
- 使用 Composables 模式封装公共逻辑
- 遵循 Vue 3 Composition API 规范

---

## 8. Tree Model UI 适配清单

### 8.1 元数据视图适配

```vue
<!-- MetadataView.vue - 处理 Tree Model 没有 tables 字段的情况 -->
<template>
  <!-- Table Model: 显示表结构 -->
  <Card v-if="metadata?.tables?.length">
    <CardHeader>
      <CardTitle>{{ $t('metadata.tables') }}</CardTitle>
    </CardHeader>
    <CardContent>
      <!-- 表格展示 TAG/FIELD 列 -->
    </CardContent>
  </Card>

  <!-- Tree Model: 显示设备/测量值结构 -->
  <Card v-else-if="metadata?.measurements?.length">
    <CardHeader>
      <CardTitle>{{ $t('metadata.measurements') }}</CardTitle>
      <CardDescription>{{ $t('metadata.treeModelHint') }}</CardDescription>
    </CardHeader>
    <CardContent>
      <!-- 测量值列表，无 TAG/FIELD 区分 -->
    </CardContent>
  </Card>
</template>
```

### 8.2 设备选择器适配

```typescript
// Tree Model 设备标识格式: root.device1, root.device2
// Table Model 设备标识格式: tablename.tag1.tag2

// 判断是否为 Tree Model 设备
function isTreeModelDevice(deviceId: string): boolean {
  return deviceId.startsWith('root.')
}

// 格式化设备显示名称
function formatDeviceName(deviceId: string): string {
  if (isTreeModelDevice(deviceId)) {
    // Tree Model: 显示完整路径
    return deviceId
  }
  // Table Model: 可以分解显示
  const parts = deviceId.split('.')
  return parts.length > 1 ? `${parts[0]} > ${parts.slice(1).join('.')}` : deviceId
}
```

### 8.3 数据预览表格适配

```vue
<!-- DataPreviewView.vue - 列头显示适配 -->
<template>
  <Table>
    <TableHeader>
      <TableRow>
        <TableHead>Time</TableHead>
        <TableHead>Device</TableHead>
        <!-- Tree Model: 所有列都是测量值 -->
        <!-- Table Model: 区分 TAG 和 FIELD 列 -->
        <TableHead
          v-for="col in columns"
          :key="col.name"
          :class="getColumnClass(col)"
        >
          {{ col.name }}
          <Badge v-if="col.columnCategory === 'TAG'" variant="secondary">TAG</Badge>
        </TableHead>
      </TableRow>
    </TableHeader>
  </Table>
</template>
```

### 8.4 国际化补充

```json
// zh-CN.json 补充
{
  "metadata": {
    "treeModelHint": "此文件使用 Tree Model 格式（V3/Legacy）",
    "tableModelHint": "此文件使用 Table Model 格式（V4）"
  },
  "device": {
    "treeModelDevice": "设备路径",
    "tableModelDevice": "设备标识"
  }
}

// en-US.json 补充
{
  "metadata": {
    "treeModelHint": "This file uses Tree Model format (V3/Legacy)",
    "tableModelHint": "This file uses Table Model format (V4)"
  },
  "device": {
    "treeModelDevice": "Device Path",
    "tableModelDevice": "Device Identifier"
  }
}
```

---

## 9. 实现优先级

| 优先级 | 功能 | 说明 |
|--------|------|------|
| P0 | Tree Model 数据预览 | 后端已支持，前端无需修改即可工作 |
| P1 | 元数据视图适配 | 优雅处理无 tables 字段的情况 |
| P1 | 设备选择器适配 | 正确显示 Tree Model 设备路径 |
| P2 | 表选择器组件 | 新增组件，仅 Table Model 需要 |
| P2 | 增强分页组件 | 通用组件，两种模型都适用 |
| P3 | 图表视图适配 | 确保 Tree Model 数据正确渲染 |

