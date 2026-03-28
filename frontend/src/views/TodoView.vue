<template>
  <div>
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-group">
        <div class="filter-label">搜索</div>
        <el-input v-model="search" placeholder="搜索名称/编号/责任人..." clearable size="default" style="width:200px" />
      </div>
      <div class="filter-group">
        <div class="filter-label">使用部门</div>
        <el-select v-model="filterDept" placeholder="全部部门" clearable size="default" style="width:130px">
          <el-option value="" label="全部部门" />
          <el-option v-for="d in depts" :key="d" :value="d" :label="d" />
        </el-select>
      </div>
      <div class="filter-group">
        <div class="filter-label">紧急程度</div>
        <el-select v-model="filterValidity" placeholder="全部" clearable size="default" style="width:120px">
          <el-option value="" label="全部" />
          <el-option value="失效" label="已失效" />
          <el-option value="即将过期" label="即将过期" />
        </el-select>
      </div>
      <div class="filter-group">
        <div class="filter-label">下次校准日期</div>
        <div class="date-range-wrap">
          <input type="date" v-model="filterDateFrom" class="date-input" title="开始日期" />
          <span class="date-range-sep">~</span>
          <input type="date" v-model="filterDateTo" class="date-input" title="结束日期" />
        </div>
      </div>
      <div class="filter-actions">
        <el-button size="default" @click="resetFilter">重置</el-button>
        <el-button size="default" @click="exportCurrent">导出当前</el-button>
        <el-button size="default" @click="exportAll">导出全部</el-button>
        <el-button size="default" type="primary" @click="loadData">刷新</el-button>
      </div>
    </div>

    <div class="batch-bar">
      <div class="batch-info">已选 <b>{{ selectedIds.length }}</b> 项</div>
      <div class="batch-actions">
        <el-button size="small" @click="toggleSelectCurrentPage">
          {{ allCurrentPageSelected ? '取消当前页' : '全选当前页' }}
        </el-button>
        <el-button size="small" :disabled="!selectedIds.length" @click="clearSelection">清空选择</el-button>
        <el-button v-if="canUpdate" size="small" type="primary" :disabled="!selectedIds.length" @click="openBatchCalib">批量记录校准</el-button>
      </div>
    </div>

    <!-- 汇总卡片 -->
    <div class="stats-grid" style="margin-bottom:16px">
      <div class="stat-card">
        <div class="stat-icon red">
          <el-icon size="22" color="#dc2626"><Warning /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">已失效</div>
          <div class="stat-value red">{{ countByV('失效') }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orange">
          <el-icon size="22" color="#d97706"><Bell /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">即将过期</div>
          <div class="stat-value orange">{{ countByV('即将过期') }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon blue">
          <el-icon size="22" color="#2563eb"><List /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">待处理合计</div>
          <div class="stat-value">{{ allTodos.length }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon blue">
          <el-icon size="22" color="#2563eb"><OfficeBuilding /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">涉及部门</div>
          <div class="stat-value">{{ involvedDepts }}</div>
        </div>
      </div>
    </div>

    <!-- 桌面表格 -->
    <div class="table-wrap">
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th style="width:54px">
                <input type="checkbox" :checked="allCurrentPageSelected" @change="toggleSelectCurrentPage" />
              </th>
              <th>紧急程度</th><th>仪器名称</th><th>计量编号</th><th>使用部门</th>
              <th>使用责任人</th><th>上次校准</th><th>下次校准/到期</th><th>逾期天数</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="filtered.length===0" class="empty-row"><td colspan="10">暂无待办事项</td></tr>
            <tr v-for="d in paged" :key="d.id" :style="{ background: d.validity==='失效'?'#fff5f5':d.validity==='即将过期'?'#fffbeb':'' }">
              <td>
                <input type="checkbox" :checked="isSelected(d.id)" @change="toggleSelection(d.id)" />
              </td>
              <td>
                <span v-if="d.validity==='失效'" class="tag tag-expired">已失效</span>
                <span v-else class="tag tag-warning">即将过期</span>
              </td>
              <td><span style="font-weight:600">{{ d.name }}</span></td>
              <td><code style="font-size:12px;background:#f1f5f9;padding:2px 6px;border-radius:4px">{{ d.metricNo }}</code></td>
              <td>{{ d.dept || '-' }}</td>
              <td>{{ d.responsiblePerson || '-' }}</td>
              <td>{{ d.calDate || '-' }}</td>
              <td style="font-weight:600" :style="{ color: d.validity==='失效'?'var(--danger)':'var(--warning)' }">{{ d.nextDate || '-' }}</td>
              <td>
                <span v-if="d.daysPassed" :class="['tag', d.validity==='失效'?'tag-expired':'tag-warning']">
                  {{ d.daysPassed }} 天
                </span>
              </td>
              <td>
                <el-button v-if="canUpdate" size="small" type="primary" plain @click="openCalib(d)">记录校准</el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 移动端卡片 -->
    <div class="mobile-list">
      <div v-if="filtered.length===0" style="text-align:center;padding:48px 0;color:var(--text-muted)">暂无待办事项</div>
      <div v-for="d in paged" :key="d.id" class="m-card" :style="{ borderLeft: d.validity==='失效'?'4px solid var(--danger)':'4px solid var(--warning)' }">
        <div class="m-card-row">
          <div style="display:flex;align-items:center;gap:10px;min-width:0;flex:1">
            <input type="checkbox" :checked="isSelected(d.id)" @change="toggleSelection(d.id)" />
            <div class="m-card-title">{{ d.name }}</div>
          </div>
          <span :class="['tag', d.validity==='失效'?'tag-expired':'tag-warning']" style="margin-left:8px;flex-shrink:0">
            {{ d.validity==='失效' ? '失效' : '即将过期' }}
          </span>
        </div>
        <div class="m-card-meta">
          <div class="m-card-meta-item">编号 <b>{{ d.metricNo }}</b></div>
          <div class="m-card-meta-item">部门 <b>{{ d.dept||'-' }}</b></div>
          <div class="m-card-meta-item">责任人 <b>{{ d.responsiblePerson||'-' }}</b></div>
          <div class="m-card-meta-item">下次校准 <b :style="{ color: d.validity==='失效'?'var(--danger)':'var(--warning)' }">{{ d.nextDate||'-' }}</b></div>
          <div v-if="d.daysPassed" class="m-card-meta-item">已逾期 <b style="color:var(--danger)">{{ d.daysPassed }} 天</b></div>
        </div>
        <div class="m-card-footer">
          <div></div>
          <el-button v-if="canUpdate" size="small" type="primary" plain @click="openCalib(d)">记录校准</el-button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="filtered.length"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      background
      @size-change="page=1"
    />

    <!-- 校准记录弹窗 -->
    <div v-if="showCalib" class="modal-mask" @click.self="closeCalib">
      <div class="modal-box modal-sm">
        <div class="modal-header">
          <div class="modal-title">{{ batchMode ? '批量记录校准' : `记录校准 — ${cf.name}` }}</div>
          <button class="modal-close" @click="closeCalib">✕</button>
        </div>
        <form @submit.prevent="saveCalib">
          <div class="modal-body" style="display:flex;flex-direction:column;gap:14px">
            <div class="form-group">
              <label class="form-label required">本次校准时间</label>
              <input v-model="cf.calDate" type="date" required style="width:100%" />
            </div>
            <div class="form-group">
              <label class="form-label">检定周期（月）</label>
              <input v-model.number="cf.cycle" type="number" min="1" style="width:100%" />
            </div>
            <div class="form-group">
              <label class="form-label">校准结果判定</label>
              <select v-model="cf.calibrationResult" style="width:100%">
                <option value="">请选择</option>
                <option>合格</option><option>不合格</option><option>降级使用</option><option>停用</option>
              </select>
            </div>
            <div v-if="batchMode" class="batch-hint">将同步更新 {{ selectedIds.length }} 条待办记录对应设备。</div>
          </div>
          <div class="modal-footer">
            <el-button @click="closeCalib">取消</el-button>
            <el-button type="primary" native-type="submit" :loading="saving">{{ saving?'保存中...':'保存' }}</el-button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, inject } from 'vue'
import * as XLSX from 'xlsx'
import { deviceApi, deptApi } from '../api/index.js'
import { useAuthStore } from '../stores/auth.js'

const showToast = inject('showToast')
const authStore = useAuthStore()
const canUpdate = computed(() => authStore.canUpdate)
const allTodos = ref([])
const search = ref(''), filterDept = ref(''), filterValidity = ref('')
const filterDateFrom = ref(''), filterDateTo = ref('')
const page = ref(1), pageSize = ref(20)
const showCalib = ref(false), saving = ref(false)
const selectedIds = ref([])
const batchMode = ref(false)
const cf = reactive({ id:null, name:'', calDate:'', cycle:12, calibrationResult:'' })
const depts = ref([])
const involvedDepts = computed(() => new Set(allTodos.value.map(d => d.dept).filter(Boolean)).size)

const filtered = computed(() => {
  let list = allTodos.value
  if (search.value) {
    const s = search.value.toLowerCase()
    list = list.filter(d =>
      d.name.toLowerCase().includes(s) ||
      (d.metricNo||'').toLowerCase().includes(s) ||
      (d.responsiblePerson||'').toLowerCase().includes(s)
    )
  }
  if (filterDept.value) list = list.filter(d => d.dept === filterDept.value)
  if (filterValidity.value) list = list.filter(d => d.validity === filterValidity.value)
  if (filterDateFrom.value || filterDateTo.value) {
    list = list.filter(d => {
      const nd = d.nextDate || ''
      return (!filterDateFrom.value || nd >= filterDateFrom.value) &&
             (!filterDateTo.value   || nd <= filterDateTo.value)
    })
  }
  return list
})

const paged = computed(() => filtered.value.slice((page.value-1)*pageSize.value, page.value*pageSize.value))
const currentPageIds = computed(() => paged.value.map(d => d.id))
const allCurrentPageSelected = computed(() =>
  currentPageIds.value.length > 0 && currentPageIds.value.every(id => selectedIds.value.includes(id))
)

function resetFilter() {
  search.value = ''; filterDept.value = ''; filterValidity.value = ''
  filterDateFrom.value = ''; filterDateTo.value = ''; page.value = 1
}
function countByV(v) { return allTodos.value.filter(d => d.validity === v).length }
function isSelected(id) { return selectedIds.value.includes(id) }
function toggleSelection(id) {
  selectedIds.value = isSelected(id)
    ? selectedIds.value.filter(item => item !== id)
    : [...selectedIds.value, id]
}
function clearSelection() { selectedIds.value = [] }
function toggleSelectCurrentPage() {
  if (allCurrentPageSelected.value) {
    selectedIds.value = selectedIds.value.filter(id => !currentPageIds.value.includes(id))
    return
  }
  selectedIds.value = Array.from(new Set([...selectedIds.value, ...currentPageIds.value]))
}

async function loadData() {
  try {
    const res = await deviceApi.list({})
    allTodos.value = res.data.filter(d => d.validity === '失效' || d.validity === '即将过期')
    allTodos.value.sort((a,b) => {
      if (a.validity !== b.validity) return a.validity==='失效' ? -1 : 1
      return (b.daysPassed||0) - (a.daysPassed||0)
    })
    selectedIds.value = selectedIds.value.filter(id => allTodos.value.some(d => d.id === id))
  } catch(e) { console.error(e) }
}

function openCalib(d) {
  batchMode.value = false
  cf.id=d.id; cf.name=d.name; cf.cycle=d.cycle||12; cf.calibrationResult=''
  cf.calDate = new Date().toISOString().split('T')[0]
  showCalib.value = true
}
function openBatchCalib() {
  batchMode.value = true
  cf.id = null; cf.name = ''; cf.cycle = 12; cf.calibrationResult = ''
  cf.calDate = new Date().toISOString().split('T')[0]
  showCalib.value = true
}
function closeCalib() { showCalib.value = false; batchMode.value = false }

function exportRows(rows, filename) {
  const data = rows.map(d => ({
    紧急程度: d.validity === '失效' ? '已失效' : '即将过期',
    仪器名称: d.name || '',
    计量编号: d.metricNo || '',
    使用部门: d.dept || '',
    使用责任人: d.responsiblePerson || '',
    上次校准: d.calDate || '',
    下次校准: d.nextDate || '',
    逾期天数: d.daysPassed || 0,
    使用状态: d.useStatus || '',
    校准结果判定: d.calibrationResult || '',
    备注: d.remark || ''
  }))
  const wb = XLSX.utils.book_new()
  const ws = XLSX.utils.json_to_sheet(data)
  XLSX.utils.book_append_sheet(wb, ws, '待办事项')
  XLSX.writeFile(wb, filename)
}

async function saveCalib() {
  saving.value = true
  try {
    if (batchMode.value) {
      const count = selectedIds.value.length
      await Promise.all(selectedIds.value.map(id =>
        deviceApi.update(id, { calDate:cf.calDate, cycle:cf.cycle, calibrationResult:cf.calibrationResult })
      ))
      clearSelection(); closeCalib()
      showToast(`已批量更新 ${count} 条记录`)
    } else {
      await deviceApi.update(cf.id, { calDate:cf.calDate, cycle:cf.cycle, calibrationResult:cf.calibrationResult })
      closeCalib()
      showToast('校准记录已保存')
    }
    loadData()
  } catch(e) { showToast('保存失败','error') }
  finally { saving.value = false }
}

async function exportCurrent() {
  try { exportRows(filtered.value, '待办事项.xlsx') } catch(e) { showToast('导出失败','error') }
}

async function exportAll() {
  try { exportRows(allTodos.value, '待办事项-全部.xlsx') } catch(e) { showToast('导出失败','error') }
}

onMounted(async () => {
  loadData()
  try { const r = await deptApi.list(); depts.value = r.data.map(d => d.name) } catch(e) {}
})
</script>

<style scoped>
.batch-bar {
  display:flex; align-items:center; justify-content:space-between;
  gap:12px; padding:10px 14px; margin-bottom:12px;
  background:#fff; border:1px solid var(--border); border-radius:12px;
}
.batch-info { color:var(--text-muted); font-size:13px; }
.batch-actions { display:flex; gap:8px; flex-wrap:wrap; }
.batch-hint {
  padding:10px 12px; border-radius:10px;
  background:#eff6ff; color:#1d4ed8; font-size:13px;
}
</style>
