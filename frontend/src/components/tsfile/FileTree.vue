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
 * FileTree 组件 - 文件树浏览器
 *
 * 这里挂了**两棵** el-tree，用 v-show 切换而不是 v-if，目的是切进/切出搜索
 * 时不丢浏览态：
 *
 * - 浏览态：`lazy` + `:load`，由 Element Plus 负责展开箭头与目录 loading。
 *   非 lazy 的 el-tree 会把「尚未加载子节点的目录」判定成叶子从而不给箭头，
 *   所以懒加载这里必须交给组件库而不是手写。
 * - 搜索态：普通 el-tree，数据是按关键词裁剪出的「命中节点 + 祖先路径」子树，
 *   祖先节点都已有 children，箭头正常；展开态由 default-expanded-keys 接管。
 *
 * `treeData` 始终是全量数据的镜像（懒加载回来的子节点会写回），搜索裁剪基于它，
 * 因此搜索只在已加载过的范围内匹配 —— 这是懒加载树的固有限制。
 */
import type { TreeNode } from "@/api/tsfile/types";
import type { LoadFunction } from "element-plus";

import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";

import { File, FileSearch, FileText, Folder, FolderX, Search } from "lucide-vue-next";

import { fileApi } from "@/api/tsfile";
import { encodeFileId } from "@/utils/fileId";
import { formatFileSize } from "@/utils/formatSize";

const { t } = useI18n();

const emit = defineEmits<{
  select: [fileId: string, path: string, name: string];
  selectDirectory: [path: string, name: string];
}>();

interface FlatNode {
  key: string;
  title: string;
  path: string;
  isDirectory: boolean;
  isLeaf: boolean;
  /** 文件大小（字节），目录没有这个字段 */
  size?: number;
  children?: FlatNode[];
}

/** el-tree 的字段映射：我们的节点用 title / children，叶子标记用 isLeaf */
const treeProps = {
  label: "title",
  children: "children",
  isLeaf: "isLeaf",
} as const;

const treeData = ref<FlatNode[]>([]);
const loading = ref(false);
const hasError = ref(false);
const searchValue = ref("");

const isSearching = computed(() => searchValue.value.trim().length > 0);

// 浏览态下用户手动展开的节点，切出搜索后用它恢复展开状态
const manualExpandedKeys = ref<string[]>([]);

/**
 * 收集所有 title 命中搜索词的节点 key，及其祖先 key（用于自动展开）。
 * 树是懒加载的：只在已加载（已展开过）的节点范围内匹配。
 */
function collectMatchedKeys(
  nodes: FlatNode[],
  keyword: string,
  ancestors: string[],
  out: Set<string>,
): boolean {
  let anyMatch = false;
  for (const node of nodes) {
    const selfMatch = node.title.toLowerCase().includes(keyword);
    let childMatch = false;
    if (node.children && node.children.length > 0) {
      childMatch = collectMatchedKeys(
        node.children,
        keyword,
        [...ancestors, node.key],
        out,
      );
    }
    if (selfMatch || childMatch) {
      // 命中节点的所有祖先都要展开才能看到它
      for (const a of ancestors) out.add(a);
      if (childMatch) out.add(node.key);
      anyMatch = true;
    }
  }
  return anyMatch;
}

const matchedKeys = computed<string[]>(() => {
  const keyword = searchValue.value.trim().toLowerCase();
  if (!keyword) return [];
  const out = new Set<string>();
  collectMatchedKeys(treeData.value, keyword, [], out);
  return [...out];
});

/**
 * 按搜索词把树裁剪为「命中节点 + 其祖先路径」的子树。
 * 保留规则：节点自身 title 命中，或其后代中有命中项（祖先需保留以展示路径）。
 */
function filterTree(nodes: FlatNode[], keyword: string): FlatNode[] {
  const result: FlatNode[] = [];
  for (const node of nodes) {
    const selfMatch = node.title.toLowerCase().includes(keyword);
    const filteredChildren =
      node.children && node.children.length > 0
        ? filterTree(node.children, keyword)
        : [];
    if (selfMatch || filteredChildren.length > 0) {
      result.push({
        ...node,
        children: filteredChildren.length > 0 ? filteredChildren : node.children,
      });
    }
  }
  return result;
}

// 搜索态展示的数据：按关键词裁剪后的子树
const filteredTreeData = computed<FlatNode[]>(() => {
  const keyword = searchValue.value.trim().toLowerCase();
  if (!keyword) return [];
  return filterTree(treeData.value, keyword);
});

function transformNode(node: TreeNode): FlatNode {
  const result: FlatNode = {
    key: node.path,
    title: node.name,
    path: node.path,
    isDirectory: node.isDirectory,
    isLeaf: !node.isDirectory,
    size: node.size,
  };
  if (node.children && node.children.length > 0) {
    result.children = node.children.map((child) => transformNode(child));
  }
  return result;
}

function extractChildren(response: unknown): TreeNode[] {
  if (Array.isArray(response)) return response;
  if (response && typeof response === "object" && "children" in response) {
    return (response as { children?: TreeNode[] }).children ?? [];
  }
  return [];
}

/**
 * 递归查找节点并设置 children
 */
function setNodeChildren(
  nodes: FlatNode[],
  key: string,
  children: FlatNode[],
): FlatNode[] {
  return nodes.map((node) => {
    if (node.key === key) {
      return { ...node, children };
    }
    if (node.children) {
      return { ...node, children: setNodeChildren(node.children, key, children) };
    }
    return node;
  });
}

async function loadRootTree() {
  loading.value = true;
  hasError.value = false;
  try {
    const response = await fileApi.getTree();
    const children = extractChildren(response);
    treeData.value = children.map((node: TreeNode) => transformNode(node));
  } catch {
    treeData.value = [];
    hasError.value = true;
  } finally {
    loading.value = false;
  }
}

// 根数据只拉一次，el-tree 的 lazy 初始化和 onMounted 会同时要它
let rootPromise: Promise<void> | null = null;
function ensureRootLoaded(): Promise<void> {
  rootPromise ??= loadRootTree();
  return rootPromise;
}

/**
 * el-tree 的懒加载回调。除了把子节点交给组件库（resolve），还要写回 treeData，
 * 否则搜索裁剪看不到这些节点。
 */
const loadNode: LoadFunction = async (node, resolve) => {
  if (node.level === 0) {
    await ensureRootLoaded();
    resolve(treeData.value);
    return;
  }

  const data = node.data as FlatNode;
  if (!data.isDirectory) {
    resolve([]);
    return;
  }
  // 已有子节点则不重复请求
  if (data.children && data.children.length > 0) {
    resolve(data.children);
    return;
  }

  try {
    const response = await fileApi.getTree(undefined, data.key);
    const children = extractChildren(response).map((n: TreeNode) => transformNode(n));
    treeData.value = setNodeChildren(treeData.value, data.key, children);
    resolve(children);
  } catch {
    treeData.value = setNodeChildren(treeData.value, data.key, []);
    resolve([]);
  }
};

// 记录浏览态的手动展开，供清空搜索后恢复。
// 注意 Element Plus 的事件签名 (data, node) 与旧组件库的 (keys, {expanded,node}) 完全不同。
function handleNodeExpand(data: FlatNode) {
  if (!manualExpandedKeys.value.includes(data.key)) {
    manualExpandedKeys.value = [...manualExpandedKeys.value, data.key];
  }
}

function handleNodeCollapse(data: FlatNode) {
  manualExpandedKeys.value = manualExpandedKeys.value.filter((k) => k !== data.key);
}

/**
 * 将文件路径编码为 URL-safe 的 Base64 fileId，见 utils/fileId.ts。
 */
function handleNodeClick(data: FlatNode) {
  if (data.isDirectory) {
    emit("selectDirectory", data.path || data.key, data.title);
  } else {
    const fileId = encodeFileId(data.path || data.key);
    emit("select", fileId, data.path || data.key, data.title);
  }
}

/**
 * 节点图标：目录 / TsFile / 普通文件。
 */
function getNodeIcon(node: FlatNode) {
  if (node.isDirectory) return { component: Folder, class: "text-warning" };
  if (node.title.endsWith(".tsfile")) {
    return { component: FileText, class: "text-primary" };
  }
  return { component: File, class: "text-text-body" };
}

/**
 * 把标题按搜索词切成「普通 / 命中」片段，供模板高亮渲染。
 * 之前是 h() 渲染函数，改成数据驱动后模板可以直接 v-for。
 */
function titleSegments(title: string): { text: string; match: boolean }[] {
  const keyword = searchValue.value.trim();
  if (!keyword) return [{ text: title, match: false }];
  const idx = title.toLowerCase().indexOf(keyword.toLowerCase());
  if (idx === -1) return [{ text: title, match: false }];

  const segments: { text: string; match: boolean }[] = [];
  if (idx > 0) segments.push({ text: title.slice(0, idx), match: false });
  segments.push({ text: title.slice(idx, idx + keyword.length), match: true });
  if (idx + keyword.length < title.length) {
    segments.push({ text: title.slice(idx + keyword.length), match: false });
  }
  return segments;
}

onMounted(() => {
  void ensureRootLoaded();
});
</script>

<template>
  <div class="file-tree">
    <div class="mb-3 flex-shrink-0">
      <h3 class="mb-2 text-[0.8125rem] font-normal uppercase tracking-wider text-text-body">
        {{ t("tsfile.file.browser") }}
      </h3>
      <el-input
        v-model="searchValue"
        :placeholder="t('tsfile.file.searchPlaceholder')"
        clearable
        size="small"
      >
        <template #prefix>
          <Search class="h-3.5 w-3.5 text-text-body" :stroke-width="1.75" />
        </template>
      </el-input>
    </div>

    <el-alert
      v-if="hasError"
      type="warning"
      :title="t('tsfile.file.loadTreeError')"
      show-icon
      :closable="false"
      class="mb-3 flex-shrink-0"
    />

    <div v-loading="loading" class="min-h-0 flex-1 overflow-y-auto">
      <!-- 浏览态：懒加载树。用 v-show 保持挂载，切出搜索后展开态不丢 -->
      <el-tree
        v-show="!isSearching"
        :props="treeProps"
        node-key="key"
        lazy
        :load="loadNode"
        :indent="12"
        :default-expanded-keys="manualExpandedKeys"
        highlight-current
        @node-expand="handleNodeExpand"
        @node-collapse="handleNodeCollapse"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <!-- 侧栏窄，文件名多半会被截断，用原生 title 让悬停能看到全名 -->
          <span class="inline-flex min-w-0 items-center gap-1.5" :title="data.title">
            <component
              :is="getNodeIcon(data).component"
              class="h-4 w-4 flex-shrink-0"
              :class="getNodeIcon(data).class"
              :stroke-width="1.75"
            />
            <span class="truncate">{{ data.title }}</span>
            <span v-if="data.size !== undefined" class="tc-file-size">{{ formatFileSize(data.size) }}</span>
          </span>
        </template>
      </el-tree>

      <!-- 搜索态：裁剪后的子树，命中关键词高亮 -->
      <template v-if="isSearching">
        <el-tree
          v-if="filteredTreeData.length > 0"
          :data="filteredTreeData"
          :props="treeProps"
          node-key="key"
          :indent="12"
          :default-expanded-keys="matchedKeys"
          highlight-current
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <span class="inline-flex min-w-0 items-center gap-1.5" :title="data.title">
              <component
                :is="getNodeIcon(data).component"
                class="h-4 w-4 flex-shrink-0"
                :class="getNodeIcon(data).class"
                :stroke-width="1.75"
              />
              <span class="truncate">
                <template v-for="(segment, index) in titleSegments(data.title)" :key="index">
                  <span v-if="segment.match" class="tc-mark">{{ segment.text }}</span>
                  <template v-else>{{ segment.text }}</template>
                </template>
              </span>
              <span v-if="data.size !== undefined" class="tc-file-size">{{ formatFileSize(data.size) }}</span>
            </span>
          </template>
        </el-tree>

        <div v-else-if="treeData.length > 0" class="py-6 text-center text-text-body">
          <FileSearch
            class="mb-2 inline-block h-9 w-9 opacity-70"
            :stroke-width="1.5"
          />
          <p class="mx-2 text-xs leading-relaxed">
            {{ t("tsfile.file.searchNoResult") }}
          </p>
        </div>
      </template>
    </div>

    <div
      v-if="!loading && !hasError && treeData.length === 0"
      class="py-6 text-center text-text-body"
    >
      <FolderX
        class="mb-2 inline-block h-9 w-9 text-warning opacity-70"
        :stroke-width="1.5"
      />
      <p class="mx-2 text-xs leading-relaxed">{{ t("tsfile.file.emptyTreeHint") }}</p>
    </div>
  </div>
</template>

<style scoped>
.file-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  user-select: none;
}

/* 侧栏很窄，压掉 el-tree 默认的缩进，把省下的横向空间让给文件名 */
.file-tree :deep(.el-tree) {
  background-color: transparent;
  font-size: 0.8125rem;
}

.file-tree :deep(.el-tree-node__content) {
  height: 2rem;
  padding-right: 0.25rem;
  border-radius: 0.375rem;
  color: var(--text-label);
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

/* hover 态对齐 `.tc-nav-item` 的交互语汇：primary 5% 透明底 */
.file-tree :deep(.el-tree-node__content:hover) {
  background-color: color-mix(in srgb, var(--primary) 5%, transparent);
  color: var(--text-heading);
}

.file-tree
  :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) {
  background-color: color-mix(in srgb, var(--primary) 8%, transparent);
  color: var(--primary);
}

/* 展开箭头默认 6px 内边距，收窄后每行能多露两三个字符 */
.file-tree :deep(.el-tree-node__expand-icon) {
  padding: 0.125rem;
}

/* 文件大小徽章：靠右对齐，不参与文件名的省略计算；等宽数字避免上下行错位 */
.tc-file-size {
  margin-left: auto;
  flex-shrink: 0;
  padding-left: 0.25rem;
  font-size: 0.6875rem;
  font-variant-numeric: tabular-nums;
  color: var(--text-body);
}
</style>
