<template>
  <div>
    <!-- 工具栏 -->
    <div class="toolbar" style="margin-bottom:12px">
      <div class="toolbar-left" style="align-items:center;gap:8px">
        <!-- 面包屑 -->
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>
            <span class="breadcrumb-link" @click="goToRoot">全部文件</span>
          </el-breadcrumb-item>
          <el-breadcrumb-item v-for="crumb in breadcrumbs" :key="crumb.id">
            <span class="breadcrumb-link" @click="openFolder(crumb.id, crumb.name)">{{ crumb.name }}</span>
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
      <div class="toolbar-right">
        <el-input
          v-model="searchQuery"
          placeholder="搜索文件..."
          clearable
          size="default"
          style="width:200px"
          @input="onSearchInput"
          @clear="clearSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button size="default" @click="openCreateFolder">
          <el-icon><FolderAdd /></el-icon> 新建文件夹
        </el-button>
        <el-button type="primary" size="default" @click="triggerUpload">
          <el-icon><Upload /></el-icon> 上传文件
        </el-button>
        <input ref="uploadRef" type="file" multiple style="display:none" @change="handleUpload" />
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" style="text-align:center;padding:60px 0;color:var(--text-muted)">
      <el-icon class="is-loading" size="32"><Loading /></el-icon>
      <div style="margin-top:12px;font-size:13px">加载中...</div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="displayItems.length === 0" style="text-align:center;padding:80px 0;color:var(--text-muted)">
      <el-icon size="56" color="#cbd5e1"><FolderOpened /></el-icon>
      <div style="margin-top:16px;font-size:15px;font-weight:600;color:#94a3b8">{{ searchQuery ? '未找到匹配文件' : '此文件夹为空' }}</div>
      <div style="margin-top:6px;font-size:13px">{{ searchQuery ? '请尝试其他关键词' : '点击上方按钮上传文件或新建文件夹' }}</div>
    </div>

    <!-- 文件网格 -->
    <div v-else class="files-grid">
      <div
        v-for="item in displayItems"
        :key="item.id"
        class="file-item"
        @dblclick="item.isFolder ? openFolder(item.id, item.name) : null"
        @click.stop
      >
        <!-- 文件操作按钮 -->
        <div class="file-actions">
          <el-popconfirm
            v-if="!item.isFolder"
            title="确定要下载此文件吗？"
            confirm-button-text="下载"
            cancel-button-text="取消"
            @confirm="downloadFile(item)"
          >
            <template #reference>
              <div class="file-action-btn" title="下载">
                <el-icon size="12"><Download /></el-icon>
              </div>
            </template>
          </el-popconfirm>
          <div class="file-action-btn" title="重命名" @click.stop="openRename(item)">
            <el-icon size="12"><Edit /></el-icon>
          </div>
          <el-popconfirm
            title="确定要删除吗？"
            confirm-button-text="删除"
            confirm-button-type="danger"
            cancel-button-text="取消"
            @confirm="deleteItem(item)"
          >
            <template #reference>
              <div class="file-action-btn danger" title="删除" @click.stop>
                <el-icon size="12"><Delete /></el-icon>
              </div>
            </template>
          </el-popconfirm>
        </div>

        <!-- 文件图标 -->
        <div class="file-icon" @click="item.isFolder ? openFolder(item.id, item.name) : null">
          {{ item.isFolder ? '📁' : getFileIcon(item.name) }}
        </div>

        <!-- 文件名 -->
        <div class="file-name" :title="item.name">{{ item.name }}</div>

        <!-- 元信息 -->
        <div class="file-meta">
          {{ item.isFolder ? '文件夹' : formatSize(item.size) }}
        </div>
      </div>
    </div>

    <!-- 新建文件夹对话框 -->
    <el-dialog
      v-model="showCreateFolderDialog"
      title="新建文件夹"
      width="400px"
      :close-on-click-modal="false"
    >
      <div class="form-group">
        <label class="form-label required">文件夹名称</label>
        <el-input
          v-model="newFolderName"
          placeholder="请输入文件夹名称"
          maxlength="100"
          show-word-limit
          @keyup.enter="createFolder"
          autofocus
        />
      </div>
      <template #footer>
        <el-button @click="showCreateFolderDialog = false">取消</el-button>
        <el-button type="primary" :loading="createFolderLoading" @click="createFolder">创建</el-button>
      </template>
    </el-dialog>

    <!-- 重命名对话框 -->
    <el-dialog
      v-model="showRenameDialog"
      title="重命名"
      width="400px"
      :close-on-click-modal="false"
    >
      <div class="form-group">
        <label class="form-label required">新名称</label>
        <el-input
          v-model="renameValue"
          :placeholder="'请输入新名称'"
          maxlength="200"
          @keyup.enter="doRename"
        />
      </div>
      <template #footer>
        <el-button @click="showRenameDialog = false">取消</el-button>
        <el-button type="primary" :loading="renameLoading" @click="doRename">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, inject, watch } from 'vue'
import { fileApi } from '../api/index.js'

const showToast = inject('showToast')

const loading = ref(false)
const items = ref([])
const breadcrumbs = ref([])
const currentFolderId = ref(null)
const uploadRef = ref(null)

const searchQuery = ref('')
const searchResults = ref([])
const isSearching = ref(false)

const showCreateFolderDialog = ref(false)
const newFolderName = ref('')
const createFolderLoading = ref(false)

const showRenameDialog = ref(false)
const renameItem = ref(null)
const renameValue = ref('')
const renameLoading = ref(false)

let searchTimer = null

const displayItems = computed(() => {
  if (isSearching.value) return searchResults.value
  return items.value
})

async function loadFiles(folderId = null) {
  loading.value = true
  try {
    const res = await fileApi.list(folderId)
    items.value = res.data
  } catch(e) {
    showToast('加载文件列表失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadBreadcrumb(folderId) {
  if (!folderId) { breadcrumbs.value = []; return }
  try {
    const res = await fileApi.breadcrumb(folderId)
    breadcrumbs.value = res.data
  } catch(e) {
    breadcrumbs.value = []
  }
}

function goToRoot() {
  currentFolderId.value = null
  breadcrumbs.value = []
  isSearching.value = false
  searchQuery.value = ''
  loadFiles(null)
}

function openFolder(id, name) {
  currentFolderId.value = id
  isSearching.value = false
  searchQuery.value = ''
  loadFiles(id)
  loadBreadcrumb(id)
}

function onSearchInput(val) {
  if (searchTimer) clearTimeout(searchTimer)
  if (!val || !val.trim()) { clearSearch(); return }
  searchTimer = setTimeout(() => { doSearch(val.trim()) }, 300)
}

async function doSearch(q) {
  isSearching.value = true
  loading.value = true
  try {
    const res = await fileApi.search(q)
    searchResults.value = res.data
  } catch(e) {
    showToast('搜索失败', 'error')
  } finally {
    loading.value = false
  }
}

function clearSearch() {
  isSearching.value = false
  searchResults.value = []
  loadFiles(currentFolderId.value)
}

function openCreateFolder() {
  newFolderName.value = ''
  showCreateFolderDialog.value = true
}

async function createFolder() {
  const name = newFolderName.value.trim()
  if (!name) { showToast('请输入文件夹名称', 'error'); return }
  createFolderLoading.value = true
  try {
    await fileApi.createFolder(name, currentFolderId.value)
    showToast('文件夹创建成功')
    showCreateFolderDialog.value = false
    loadFiles(currentFolderId.value)
  } catch(e) {
    showToast(e.response?.data?.message || '创建失败', 'error')
  } finally {
    createFolderLoading.value = false
  }
}

function triggerUpload() { uploadRef.value?.click() }

async function handleUpload(e) {
  const files = Array.from(e.target.files)
  if (!files.length) return
  let successCount = 0
  for (const file of files) {
    try {
      await fileApi.upload(file, currentFolderId.value)
      successCount++
    } catch(err) {
      showToast(`上传 ${file.name} 失败`, 'error')
    }
  }
  if (successCount > 0) {
    showToast(`成功上传 ${successCount} 个文件`)
    loadFiles(currentFolderId.value)
  }
  e.target.value = ''
}

async function downloadFile(item) {
  try {
    const res = await fileApi.download(item.id)
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = item.name
    a.click()
    URL.revokeObjectURL(url)
  } catch(e) {
    showToast('下载失败', 'error')
  }
}

async function deleteItem(item) {
  try {
    await fileApi.delete(item.id)
    showToast('已删除')
    if (isSearching.value) {
      searchResults.value = searchResults.value.filter(i => i.id !== item.id)
    } else {
      items.value = items.value.filter(i => i.id !== item.id)
    }
  } catch(e) {
    showToast(e.response?.data?.message || '删除失败', 'error')
  }
}

function openRename(item) {
  renameItem.value = item
  renameValue.value = item.name
  showRenameDialog.value = true
}

async function doRename() {
  const name = renameValue.value.trim()
  if (!name) { showToast('请输入名称', 'error'); return }
  if (name === renameItem.value.name) { showRenameDialog.value = false; return }
  renameLoading.value = true
  try {
    await fileApi.rename(renameItem.value.id, name)
    showToast('重命名成功')
    showRenameDialog.value = false
    if (isSearching.value) {
      const idx = searchResults.value.findIndex(i => i.id === renameItem.value.id)
      if (idx !== -1) searchResults.value[idx] = { ...searchResults.value[idx], name }
    } else {
      const idx = items.value.findIndex(i => i.id === renameItem.value.id)
      if (idx !== -1) items.value[idx] = { ...items.value[idx], name }
    }
  } catch(e) {
    showToast(e.response?.data?.message || '重命名失败', 'error')
  } finally {
    renameLoading.value = false
  }
}

function getFileIcon(name) {
  if (!name) return '📄'
  const ext = name.split('.').pop().toLowerCase()
  const map = {
    pdf: '📕', doc: '📘', docx: '📘', xls: '📗', xlsx: '📗',
    ppt: '📙', pptx: '📙', txt: '📄', csv: '📊',
    jpg: '🖼️', jpeg: '🖼️', png: '🖼️', gif: '🖼️', bmp: '🖼️', webp: '🖼️',
    mp4: '🎬', avi: '🎬', mov: '🎬', mkv: '🎬',
    mp3: '🎵', wav: '🎵', flac: '🎵',
    zip: '🗜️', rar: '🗜️', '7z': '🗜️', tar: '🗜️', gz: '🗜️',
    js: '💻', ts: '💻', vue: '💻', html: '💻', css: '💻', json: '💻',
  }
  return map[ext] || '📄'
}

function formatSize(bytes) {
  if (!bytes || bytes === 0) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

onMounted(() => {
  loadFiles(null)
})
</script>

<style scoped>
.breadcrumb-link {
  cursor: pointer;
  color: var(--primary);
  font-weight: 500;
  transition: color 0.15s;
}
.breadcrumb-link:hover { color: var(--primary-dark); text-decoration: underline; }
</style>
