<template>
  <div>
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-group">
        <div class="filter-label">搜索</div>
        <div class="search-wrap">
          <input v-model="search" class="search-input" placeholder="部门名称/编码/描述..." @input="onSearch" style="width:200px" />
        </div>
      </div>
      <div class="filter-actions">
        <button class="btn btn-outline" @click="downloadTemplate" title="下载导入模板">📥 模板</button>
        <label class="btn btn-outline" style="cursor:pointer" title="从Excel导入">
          📂 导入
          <input type="file" accept=".xlsx,.xls" style="display:none" @change="handleImport" ref="importInput" />
        </label>
        <button class="btn btn-outline" @click="doExport">📤 导出筛选</button>
        <button class="btn btn-outline" @click="doExportAll">📤 导出全部</button>
        <button class="btn btn-primary" @click="openCreate()">+ 新增部门</button>
      </div>
    </div>

    <div class="batch-bar">
      <div class="batch-info">共 <b>{{ allDepts.length }}</b> 个部门（多级树形结构）</div>
      <div class="batch-actions">
        <button class="btn btn-outline" @click="expandAll">展开全部</button>
        <button class="btn btn-outline" @click="collapseAll">折叠全部</button>
      </div>
    </div>

    <!-- 树形表格 -->
    <div class="table-wrap">
      <el-table
        :data="displayTree"
        row-key="id"
        :default-expand-all="defaultExpand"
        border
        style="width:100%"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :row-class-name="({ row }) => `dept-row-depth-${Math.min(row._depth || 0, 2)}`"
      >
        <el-table-column label="部门名称" min-width="220">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <div :class="['dept-icon', `dept-icon-d${Math.min(row._depth || 0, 2)}`]">
                {{ (row._depth || 0) === 0 ? '🏢' : (row._depth === 1 ? '📂' : '📄') }}
              </div>
              <span :class="['dept-name', `dept-name-d${Math.min(row._depth || 0, 2)}`]">{{ row.name }}</span>
              <span v-if="row.children && row.children.length" class="dept-child-badge">{{ row.children.length }} 子部门</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="部门编码" width="130">
          <template #default="{ row }">
            <span v-if="row.code" class="tag tag-gray" style="font-size:12px">{{ row.code }}</span>
            <span v-else class="text-muted text-sm">-</span>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200">
          <template #default="{ row }">
            <span class="text-sm" style="color:#475569">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80" align="center">
          <template #default="{ row }">
            <span class="text-sm text-muted">{{ row.sortOrder }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <div class="action-group" style="justify-content:center">
              <button class="action-btn action-btn-edit" @click="openCreate(row)">+ 子部门</button>
              <button class="action-btn action-btn-edit" @click="openEdit(row)">编辑</button>
              <button class="action-btn action-btn-del" @click="deleteDept(row)">删除</button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑部门弹窗 -->
    <div v-if="showModal" class="modal-mask" @click.self="closeModal">
      <div class="modal-box modal-sm">
        <div class="modal-header">
          <div class="modal-title">{{ editingId ? '✏️ 编辑部门' : '🏢 新增部门' }}</div>
          <button class="modal-close" @click="closeModal">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group" style="margin-bottom:14px">
            <label class="form-label">上级部门</label>
            <el-select v-model="form.parentId" placeholder="无（顶级部门）" clearable style="width:100%">
              <el-option :value="null" label="无（顶级部门）" />
              <el-option
                v-for="d in flatParentOptions"
                :key="d.id"
                :value="d.id"
                :label="d.label"
                :disabled="d.id === editingId"
              />
            </el-select>
          </div>
          <div class="form-group" style="margin-bottom:14px">
            <label class="form-label">部门名称 <span style="color:red">*</span></label>
            <input class="form-input" v-model="form.name" placeholder="如：研发部、生产一部" />
          </div>
          <div class="form-group" style="margin-bottom:14px">
            <label class="form-label">部门编码</label>
            <input class="form-input" v-model="form.code" placeholder="如：R&D、PROD1（可选）" />
          </div>
          <div class="form-group" style="margin-bottom:14px">
            <label class="form-label">描述</label>
            <textarea class="form-input" v-model="form.description" placeholder="部门职能描述（可选）" rows="3" style="resize:vertical"></textarea>
          </div>
          <div class="form-group">
            <label class="form-label">排序号</label>
            <input class="form-input" type="number" v-model.number="form.sortOrder" placeholder="数字越小排越前，默认0" min="0" />
          </div>
          <div v-if="formError" style="margin-top:12px;padding:10px 14px;background:#fef2f2;border:1px solid #fecaca;color:#991b1b;border-radius:8px;font-size:13px">⚠ {{ formError }}</div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="saveForm" :disabled="saving">{{ saving ? '保存中...' : (editingId ? '保存修改' : '创建部门') }}</button>
        </div>
      </div>
    </div>

    <!-- 导入结果弹窗 -->
    <div v-if="importResult" class="modal-mask" @click.self="importResult=null">
      <div class="modal-box modal-sm">
        <div class="modal-header">
          <div class="modal-title">📊 导入结果</div>
          <button class="modal-close" @click="importResult=null">✕</button>
        </div>
        <div class="modal-body">
          <div style="display:flex;gap:16px;margin-bottom:16px">
            <div class="import-stat import-stat-ok">
              <div class="import-stat-num">{{ importResult.success }}</div>
              <div class="import-stat-label">成功</div>
            </div>
            <div class="import-stat import-stat-fail">
              <div class="import-stat-num">{{ importResult.failed }}</div>
              <div class="import-stat-label">失败/跳过</div>
            </div>
          </div>
          <div v-if="importResult.errors && importResult.errors.length" style="max-height:200px;overflow-y:auto">
            <div v-for="(e, i) in importResult.errors" :key="i" style="font-size:12.5px;color:#7f1d1d;padding:4px 0;border-bottom:1px solid #fecaca">{{ e }}</div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-primary" @click="importResult=null">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, inject, computed } from 'vue'
import { deptApi } from '../api/index.js'

const showToast = inject('showToast')
const deptTree = ref([])   // tree for table display
const allDepts = ref([])   // flat list for selectors
const search = ref('')
const showModal = ref(false)
const saving = ref(false)
const editingId = ref(null)
const formError = ref('')
const importResult = ref(null)
const importInput = ref(null)
const defaultExpand = ref(true)

const form = reactive({ name: '', code: '', description: '', sortOrder: 0, parentId: null })

// Flat list for parent selector (with indentation label)
const flatParentOptions = computed(() => {
  const result = []
  function flatten(nodes, depth) {
    for (const n of nodes) {
      result.push({ id: n.id, label: '　'.repeat(depth) + n.name })
      if (n.children && n.children.length) flatten(n.children, depth + 1)
    }
  }
  flatten(deptTree.value, 0)
  return result
})

// Recursively add _depth to all nodes
function addDepth(nodes, depth = 0) {
  return nodes.map(n => ({
    ...n,
    _depth: depth,
    children: n.children && n.children.length ? addDepth(n.children, depth + 1) : []
  }))
}

// Filter tree by search term, then add depth
const displayTree = computed(() => {
  if (!search.value) return addDepth(deptTree.value)
  const kw = search.value.toLowerCase()
  function filterNodes(nodes) {
    return nodes.reduce((acc, node) => {
      const match = (node.name||'').toLowerCase().includes(kw) ||
                    (node.code||'').toLowerCase().includes(kw) ||
                    (node.description||'').toLowerCase().includes(kw)
      const filteredChildren = filterNodes(node.children || [])
      if (match || filteredChildren.length) {
        acc.push({ ...node, children: filteredChildren })
      }
      return acc
    }, [])
  }
  return addDepth(filterNodes(deptTree.value))
})

async function load() {
  try {
    const [treeRes, flatRes] = await Promise.all([deptApi.tree(), deptApi.list()])
    deptTree.value = treeRes.data
    allDepts.value = flatRes.data
  } catch(e) { console.error(e) }
}

function onSearch() { /* displayTree computed handles filtering */ }
function expandAll() { defaultExpand.value = true; load() }
function collapseAll() { defaultExpand.value = false; load() }

function openCreate(parentRow) {
  editingId.value = null
  form.name = ''
  form.code = ''
  form.description = ''
  form.sortOrder = 0
  form.parentId = parentRow ? parentRow.id : null
  formError.value = ''
  showModal.value = true
}

function openEdit(d) {
  editingId.value = d.id
  form.name = d.name || ''
  form.code = d.code || ''
  form.description = d.description || ''
  form.sortOrder = d.sortOrder ?? 0
  form.parentId = d.parentId || null
  formError.value = ''
  showModal.value = true
}

function closeModal() { showModal.value = false }

async function saveForm() {
  formError.value = ''
  if (!form.name.trim()) { formError.value = '请输入部门名称'; return }
  saving.value = true
  const payload = {
    name: form.name.trim(),
    code: form.code.trim(),
    description: form.description.trim(),
    sortOrder: String(form.sortOrder ?? 0),
    parentId: form.parentId != null ? String(form.parentId) : ''
  }
  try {
    if (editingId.value) {
      await deptApi.update(editingId.value, payload)
      showToast('部门已更新')
    } else {
      await deptApi.create(payload)
      showToast('部门已创建')
    }
    closeModal()
    load()
  } catch(e) {
    formError.value = e.response?.data?.message || '操作失败'
  } finally {
    saving.value = false
  }
}

async function deleteDept(d) {
  const hasChildren = d.children && d.children.length > 0
  const tip = hasChildren ? `部门「${d.name}」有子部门，删除后子部门将变为顶级部门。确定删除？` : `确定删除部门「${d.name}」吗？删除后设备台账中该部门字段不受影响。`
  if (!confirm(tip)) return
  try {
    await deptApi.remove(d.id)
    showToast('已删除')
    load()
  } catch(e) {
    showToast(e.response?.data?.message || '删除失败', 'error')
  }
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

async function doExport() {
  try {
    const r = await deptApi.export(search.value || undefined)
    saveBlob(r.data, '部门列表.xlsx')
  } catch(e) { showToast('导出失败', 'error') }
}

async function doExportAll() {
  try {
    const r = await deptApi.exportAll()
    saveBlob(r.data, '部门列表(全部).xlsx')
  } catch(e) { showToast('导出失败', 'error') }
}

async function downloadTemplate() {
  try {
    const r = await deptApi.template()
    saveBlob(r.data, '部门导入模板.xlsx')
  } catch(e) { showToast('下载失败', 'error') }
}

async function handleImport(e) {
  const file = e.target.files[0]
  if (!file) return
  try {
    const r = await deptApi.import(file)
    importResult.value = r.data
    load()
  } catch(e) {
    showToast('导入失败', 'error')
  } finally {
    if (importInput.value) importInput.value.value = ''
  }
}

onMounted(() => load())
</script>

<style scoped>
/* Depth-based row background */
:deep(.dept-row-depth-0) td { background: #f0f7ff !important; }
:deep(.dept-row-depth-1) td { background: #f0fdf4 !important; }
:deep(.dept-row-depth-2) td { background: #fffbeb !important; }

/* Dept icon */
.dept-icon {
  width: 30px; height: 30px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 15px; flex-shrink: 0;
}
.dept-icon-d0 { background: linear-gradient(135deg, #bfdbfe, #a5b4fc); }
.dept-icon-d1 { background: linear-gradient(135deg, #bbf7d0, #6ee7b7); }
.dept-icon-d2 { background: linear-gradient(135deg, #fde68a, #fcd34d); }

/* Dept name */
.dept-name { font-weight: 600; }
.dept-name-d0 { color: #1d4ed8; font-size: 14px; }
.dept-name-d1 { color: #065f46; font-size: 13.5px; }
.dept-name-d2 { color: #92400e; font-size: 13px; }

/* Child count badge */
.dept-child-badge {
  font-size: 11px; padding: 1px 7px; border-radius: 20px;
  background: #e0e7ff; color: #4338ca; font-weight: 500;
}
.import-stat {
  flex: 1; padding: 14px; border-radius: 10px; text-align: center;
}
.import-stat-ok { background: #f0fdf4; border: 1px solid #bbf7d0; }
.import-stat-fail { background: #fef2f2; border: 1px solid #fecaca; }
.import-stat-num { font-size: 28px; font-weight: 800; }
.import-stat-ok .import-stat-num { color: #16a34a; }
.import-stat-fail .import-stat-num { color: #dc2626; }
.import-stat-label { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
.batch-bar {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:12px;
  padding:10px 14px;
  margin-bottom:12px;
  background:#fff;
  border:1px solid var(--border);
  border-radius:12px;
}
.batch-info { color:var(--text-muted); font-size:13px; }
.batch-actions { display:flex; gap:8px; flex-wrap:wrap; }
</style>
