/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * File Store - 文件状态管理
 */
import { ref } from "vue";

import { defineStore } from "pinia";

export interface FileInfo {
  fileId: string;
  name: string;
  path: string;
  size: number;
  uploadTime: string;
}

const CURRENT_FILE_KEY = "tsfile-viewer-current-file";

export const useFileStore = defineStore("tsfile-file", () => {
  const currentFileId = ref<null | string>(null);
  const currentFileName = ref<null | string>(null);
  const recentFiles = ref<FileInfo[]>([]);
  const uploadProgress = ref<number>(0);
  const isUploading = ref<boolean>(false);
  const selectedScanTarget = ref<{ path: string; type: 'file' | 'directory' } | null>(null);
  const autoStartScan = ref(false);

  function setCurrentFile(fileId: string, fileName: string) {
    currentFileId.value = fileId;
    currentFileName.value = fileName;
    try {
      localStorage.setItem(CURRENT_FILE_KEY, JSON.stringify({ fileId, fileName }));
    } catch {
      // 隐私模式 / localStorage 不可用时静默忽略
    }
  }

  function clearCurrentFile() {
    currentFileId.value = null;
    currentFileName.value = null;
    try {
      localStorage.removeItem(CURRENT_FILE_KEY);
    } catch {
      // 忽略
    }
  }

  /** 从 localStorage 恢复上次打开的 currentFile，供页面刷新后使用 */
  function restoreCurrentFile() {
    try {
      const stored = localStorage.getItem(CURRENT_FILE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        if (
          parsed &&
          typeof parsed.fileId === "string" &&
          typeof parsed.fileName === "string"
        ) {
          currentFileId.value = parsed.fileId;
          currentFileName.value = parsed.fileName;
        }
      }
    } catch {
      // 忽略
    }
  }

  function addRecentFile(file: FileInfo) {
    // Remove if already exists
    recentFiles.value = recentFiles.value.filter((f) => f.fileId !== file.fileId);
    // Add to beginning
    recentFiles.value.unshift(file);
    // Keep only last 10
    if (recentFiles.value.length > 10) {
      recentFiles.value = recentFiles.value.slice(0, 10);
    }
    // Persist to localStorage
    localStorage.setItem("tsfile-viewer-recent-files", JSON.stringify(recentFiles.value));
  }

  function loadRecentFiles() {
    const stored = localStorage.getItem("tsfile-viewer-recent-files");
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        if (isFileInfoArray(parsed)) {
          recentFiles.value = parsed;
        } else {
          localStorage.removeItem("tsfile-viewer-recent-files");
        }
      } catch {
        console.error("Failed to parse recent files from localStorage");
      }
    }
  }

  function setUploadProgress(progress: number) {
    uploadProgress.value = progress;
  }

  function setUploading(uploading: boolean) {
    isUploading.value = uploading;
  }

  function setScanTarget(path: string, type: 'file' | 'directory', autoStart = false) {
    selectedScanTarget.value = { path, type };
    autoStartScan.value = autoStart;
  }

  return {
    currentFileId,
    currentFileName,
    recentFiles,
    uploadProgress,
    isUploading,
    selectedScanTarget,
    autoStartScan,
    setCurrentFile,
    clearCurrentFile,
    restoreCurrentFile,
    addRecentFile,
    loadRecentFiles,
    setUploadProgress,
    setUploading,
    setScanTarget,
  };
});

function isFileInfoArray(value: unknown): value is FileInfo[] {
  return (
    Array.isArray(value) &&
    value.every((item) => {
      if (!item || typeof item !== "object") return false;
      const candidate = item as FileInfo;
      return (
        typeof candidate.fileId === "string" &&
        typeof candidate.name === "string" &&
        typeof candidate.path === "string" &&
        typeof candidate.size === "number" &&
        typeof candidate.uploadTime === "string"
      );
    })
  );
}
