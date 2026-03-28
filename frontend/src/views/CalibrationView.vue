<template>
  <div>
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-group">
        <div class="filter-label">搜索</div>
        <el-input v-model="search" placeholder="搜索名称/编号/责任人..." clearable size="default" style="width:200px" @input="onFilter" />
      </div>
      <div class="filter-group">
        <div class="filter-label">使用部门</div>
        <el-select v-model="filterDept" placeholder="全部部门" clearable size="default" style="width:130px" @change="onFilter">
          <el-option value="" label="全部部门" />
          <el-option v-for="d in depts" :key="d" :value="d" :label="d" />
        </el-select>
      </div>
      <div class="filter-group">
        <div class="filter-label">有效性</div>
        <el-select v-model="filterValidity" placeholder="全部状态" clearable size="default" style="width:120px" @change="onFilter">
          <el-option value="" label="全部状态" />
          <el-option value="有效" label="有效" />
          <el-option value="即将过期" label="即将过期" />
          <el-option value="失效" label="失效" />
        </el-select>
      </div>
      <div class="filter-group">
        <div class="filter-label">下次校准日期</div>
        <div class="date-range-wrap">
          <input type="date" v-model="filterDateFrom" class="date-input" title="开始日期" @change="onFilter" />
          <span class="date-range-sep">~</span>
          <input type="date" v-model="filterDateTo" class="date-input" title="结束日期" @change="onFilter" />
        </div>
      </div>
      <div class="filter-group">
        <div class="filter-label">使用责任人</div>
        <el-input v-model="filterPerson" placeholder="责任人姓名" clearable size="default" style="width:120px" @input="onFilter" />
      </div>
      <div class="filter-group">
        <div class="filter-label">使用状态</div>
        <el-select v-model="filterUseStatus" placeholder="全部状态" clearable size="default" style="width:120px" @change="onFilter">
          <el-option value="" label="全部" />
          <el-option v-for="s in deviceStatuses" :key="s.id" :value="s.name" :label="s.name" />
        </el-select>
      </div>
      <div class="filter-actions">
        <el-button size="default" @click="resetFilter">重置</el-button>
        <el-button size="default" @click="exportCal">导出当前</el-button>
        <el-button size="default" @click="exportAll">导出全部</el-button>
      </div>
    </div>

    <div class="batch-bar">
      <div class="batch-info">已选 <b>{{ selectedIds.length }}</b> 项</div>
      <div class="batch-actions">
        <el-button size="small" @click="toggleSelectCurrentPage">
          {{ allCurrentPageSelected ? '取消当前页' : '全选当前页' }}
        </el-button>
        <el-button size="small" :disabled="!selectedIds.length" @click="clearSelection">清空选择</el-button>
        <el-button v-if="authStore.canUpdate" size="small" type="primary" :disabled="!selectedIds.length" @click="openBatchCalib">批量记录校准</el-button>
      </div>
    </div>

    <!-- 汇总 -->
    <div style="display:flex;gap:10px;margin-bottom:12px;flex-wrap:wrap;align-items:center">
      <span class="tag tag-valid">有效 {{ countBy('有效') }}</span>
      <span class="tag tag-warning">即将过期 {{ countBy('即将过期') }}</span>
      <span class="tag tag-expired">失效 {{ countBy('失效') }}</span>
      <span style="font-size:13px;color:var(--text-muted)">共 {{ filtered.length }} 条</span>
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
              <th>仪器名称</th><th>计量编号</th><th>使用部门</th><th>使用责任人</th>
              <th>上次校准</th><th>下次校准</th><th>有效性</th><th>使用状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="paged.length===0" class="empty-row"><td colspan="10">暂无数据</td></tr>
            <tr v-for="d in paged" :key="d.id">
              <td>
                <input type="checkbox" :checked="isSelected(d.id)" @change="toggleSelection(d.id)" />
              </td>
              <td><span style="font-weight:500">{{ d.name }}</span></td>
              <td><code style="font-size:12px;background:#f1f5f9;padding:2px 6px;border-radius:4px">{{ d.metricNo }}</code></td>
              <td>{{ d.dept || '-' }}</td>
              <td>{{ d.responsiblePerson || '-' }}</td>
              <td>{{ d.calDate || '-' }}</td>
              <td :style="{ color: d.validity==='失效' ? 'var(--danger)' : d.validity==='即将过期' ? 'var(--warning)' : 'inherit', fontWeight: d.validity!=='有效'?600:'normal' }">
                {{ d.nextDate || '-' }}
              </td>
              <td><span :class="['tag', vTag(d.validity)]">{{ d.validity || '-' }}</span></td>
              <td><span :class="['tag', usTag(d.useStatus)]">{{ d.useStatus || '正常' }}</span></td>
              <td>
                <el-button v-if="authStore.canUpdate" size="small" type="primary" plain @click="openCalib(d)">记录校准</el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 移动端卡片 -->
    <div class="mobile-list">
      <div v-if="paged.length===0" style="text-align:center;padding:48px 0;color:var(--text-muted)">暂无数据</div>
      <div v-for="d in paged" :key="d.id" class="m-card">
        <div class="m-card-row">
          <div style="display:flex;align-items:center;gap:10px;min-width:0;flex:1">
            <input type="checkbox" :checked="isSelected(d.id)" @change="toggleSelection(d.id)" />
            <div class="m-card-title">{{ d.name }}</div>
          </div>
          <span :class="['tag', vTag(d.validity)]" style="margin-left:8px;flex-shrink:0">{{ d.validity || '-' }}</span>
        </div>
        <div class="m-card-meta">
          <div class="m-card-meta-item">编号 <b>{{ d.metricNo || '-' }}</b></div>
          <div class="m-card-meta-item">部门 <b>{{ d.dept || '-' }}</b></div>
          <div class="m-card-meta-item">责任人 <b>{{ d.responsiblePerson || '-' }}</b></div>
          <div class="m-card-meta-item">上次校准 <b>{{ d.calDate || '-' }}</b></div>
          <div class="m-card-meta-item">下次校准
            <b :style="{ color: d.validity==='失效' ? 'var(--danger)' : d.validity==='即将过期' ? 'var(--warning)' : 'inherit' }">{{ d.nextDate || '-' }}</b>
          </div>
        </div>
        <div class="m-card-footer">
          <span :class="['tag', usTag(d.useStatus)]">{{ d.useStatus || '正常' }}</span>
          <el-button v-if="authStore.canUpdate" size="small" type="primary" plain @click="openCalib(d)">记录校准</el-button>
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
    <div v-if="showModal" class="modal-mask" @click.self="closeModal">
      <div class="modal-box modal-sm">
        <div class="modal-header">
          <div class="modal-title">{{ batchMode ? '批量记录校准' : `记录校准 — ${cf.name}` }}</div>
          <button class="modal-close" @click="closeModal">✕</button>
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
            <div class="form-group">
              <label class="form-label">备注</label>
              <textarea v-model="cf.remark" rows="2" style="width:100%;resize:vertical"></textarea>
            </div>
            <div v-if="batchMode" class="batch-hint">将同步更新 {{ selectedIds.length }} 台设备的校准信息。</div>
          </div>
          <div class="modal-footer">
            <el-button @click="closeModal">取消</el-button>
            <el-button type="primary" native-type="submit" :loading="saving">{{ saving ? '保存中...' : '保存校准记录' }}</el-button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, inject } from 'vue'
import { deviceApi, deptApi, deviceStatusApi } from '../api/index.js'
import { useAuthStore } from '../stores/auth.js'

const showToast = inject('showToast')
const authStore = useAuthStore()
const devices = ref([])
const search = ref(''), filterDept = ref(''), filterValidity = ref(''), filterPerson = ref('')
const filterDateFrom = ref(''), filterDateTo = ref(''), filterUseStatus = ref('')
const deviceStatuses = ref([])
const showModal = ref(false), saving = ref(false)
const page = ref(1), pageSize = ref(20)
const selectedIds = ref([])
const batchMode = ref(false)
const cf = reactive({ id:null, name:'', calDate:'', cycle:12, calibrationResult:'', remark:'' })
const depts = ref([])

const filtered = computed(() => {
  let list = devices.value
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
  if (filterPerson.value) list = list.filter(d => (d.responsiblePerson||'').includes(filterPerson.value))
  if (filterUseStatus.value) list = list.filter(d => d.useStatus === filterUseStatus.value)
  if (filterDateFrom.value || filterDateTo.value) {
    list = list.filter(d => {
      const nd = d.nextDate || ''
      return (!filterDateFrom.value || nd >= filterDateFrom.value) &&
             (!filterDateTo.value   || nd <= filterDateTo.value)
    })
  }
  return list
})

const totalItems = computed(() => filtered.value.length)
const paged = computed(() => {
  const s = (page.value - 1) * pageSize.value
  return filtered.value.slice(s, s + pageSize.value)
})
const currentPageIds = computed(() => paged.value.map(d => d.id))
const allCurrentPageSelected = computed(() =>
  currentPageIds.value.length > 0 && currentPageIds.value.every(id => selectedIds.value.includes(id))
)

function onFilter() { page.value = 1; clearSelection() }
function resetFilter() {
  search.value = ''; filterDept.value = ''; filterValidity.value = ''
  filterPerson.value = ''; filterDateFrom.value = ''; filterDateTo.value = ''; filterUseStatus.value = ''
  page.value = 1; clearSelection(); loadDevices()
}

function vTag(v) { return v==='有效'?'tag-valid':v==='即将过期'?'tag-warning':'tag-expired' }
function usTag(s) { const m={'正常':'tag-green','故障':'tag-red','维修':'tag-yellow','报废':'tag-gray'}; return m[s]||'tag-blue' }
function countBy(v) { return filtered.value.filter(d => d.validity === v).length }
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

async function loadDevices() {
  try {
    devices.value = (await deviceApi.list({})).data
    selectedIds.value = selectedIds.value.filter(id => devices.value.some(d => d.id === id))
  } catch(e) {}
}

function openCalib(d) {
  batchMode.value = false
  cf.id = d.id; cf.name = d.name; cf.cycle = d.cycle||12; cf.remark = d.remark||''; cf.calibrationResult = d.calibrationResult||''
  cf.calDate = new Date().toISOString().split('T')[0]
  showModal.value = true
}
function openBatchCalib() {
  batchMode.value = true
  cf.id = null; cf.name = ''; cf.cycle = 12; cf.remark = ''; cf.calibrationResult = ''
  cf.calDate = new Date().toISOString().split('T')[0]
  showModal.value = true
}
function closeModal() { showModal.value = false; batchMode.value = false }

async function saveCalib() {
  saving.value = true
  try {
    if (batchMode.value) {
      const count = selectedIds.value.length
      await Promise.all(selectedIds.value.map(id =>
        deviceApi.update(id, { calDate:cf.calDate, cycle:cf.cycle, calibrationResult:cf.calibrationResult, remark:cf.remark })
      ))
      clearSelection(); closeModal()
      showToast(`已批量更新 ${count} 条校准记录`)
    } else {
      await deviceApi.update(cf.id, { calDate:cf.calDate, cycle:cf.cycle, calibrationResult:cf.calibrationResult, remark:cf.remark })
      closeModal()
      showToast('校准记录已保存')
    }
    loadDevices()
  } catch(e) { showToast('保存失败','error') }
  finally { saving.value = false }
}

async function exportCal() {
  try {
    const r = await deviceApi.exportCalibration({
      search: search.value||undefined, dept: filterDept.value||undefined,
      validity: filterValidity.value||undefined, responsiblePerson: filterPerson.value||undefined,
      useStatus: filterUseStatus.value||undefined
    })
    const url = URL.createObjectURL(r.data)
    Object.assign(document.createElement('a'), { href:url, download:'校准管理.xlsx' }).click()
    URL.revokeObjectURL(url)
  } catch(e) { showToast('导出失败','error') }
}

async function exportAll() {
  try {
    const r = await deviceApi.exportCalibration({})
    const url = URL.createObjectURL(r.data)
    Object.assign(document.createElement('a'), { href:url, download:'校准管理-全部.xlsx' }).click()
    URL.revokeObjectURL(url)
  } catch(e) { showToast('导出失败','error') }
}

onMounted(async () => {
  loadDevices()
  try { const r = await deptApi.list(); depts.value = r.data.map(d => d.name) } catch(e) {}
  try { const r = await deviceStatusApi.list(); deviceStatuses.value = r.data } catch(e) {}
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
