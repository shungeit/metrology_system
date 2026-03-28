<template>
  <div class="audit-view">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 待审批（仅管理员） -->
      <el-tab-pane v-if="authStore.isAdmin" label="待审批" name="pending">
        <div class="tab-toolbar">
          <span class="record-count">共 <b>{{ pendingList.length }}</b> 条待审批</span>
          <el-button size="small" @click="loadPending">刷新</el-button>
        </div>
        <el-table :data="pendingList" border stripe v-loading="loading.pending" empty-text="暂无待审批记录">
          <el-table-column prop="submittedBy" label="提交人" width="100" />
          <el-table-column label="操作类型" width="85">
            <template #default="{ row }">
              <el-tag :type="typeTagType(row.type)" size="small" effect="light">{{ typeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="设备名称" min-width="160">
            <template #default="{ row }">
              <span style="font-weight:500">{{ deviceName(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="submittedAt" label="提交时间" width="150">
            <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip />
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button size="small" plain @click="openDetail(row)">查看详情</el-button>
              <el-button size="small" type="success" @click="openApprove(row)">审批通过</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 我的申请 -->
      <el-tab-pane label="我的申请" name="my">
        <div class="tab-toolbar">
          <el-button size="small" @click="loadMy">刷新</el-button>
        </div>
        <el-table :data="myList" border stripe v-loading="loading.my" empty-text="暂无申请记录">
          <el-table-column label="操作类型" width="85">
            <template #default="{ row }">
              <el-tag :type="typeTagType(row.type)" size="small" effect="light">{{ typeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="设备名称" min-width="160">
            <template #default="{ row }"><span style="font-weight:500">{{ deviceName(row) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="submittedAt" label="提交时间" width="150">
            <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column prop="approvedAt" label="审批时间" width="150">
            <template #default="{ row }">{{ formatTime(row.approvedAt) }}</template>
          </el-table-column>
          <el-table-column label="驳回原因" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.rejectReason" style="color:#dc2626">{{ row.rejectReason }}</span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button size="small" plain @click="openDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 审批流程说明 -->
        <div class="workflow-hint">
          <div class="workflow-title">审批流程说明</div>
          <div class="workflow-steps-row">
            <div class="wf-step wf-step-done">
              <div class="wf-step-icon">①</div>
              <div class="wf-step-label">普通用户<br>提交变更申请</div>
            </div>
            <div class="wf-arrow">→</div>
            <div class="wf-step wf-step-active">
              <div class="wf-step-icon">②</div>
              <div class="wf-step-label">管理员<br>审核审批</div>
            </div>
            <div class="wf-arrow">→</div>
            <div class="wf-step wf-step-pending">
              <div class="wf-step-icon">③</div>
              <div class="wf-step-label">审批通过<br>自动执行生效</div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 审批历史（仅管理员） -->
      <el-tab-pane v-if="authStore.isAdmin" label="审批历史" name="history">
        <div class="tab-toolbar">
          <el-button size="small" @click="loadHistory">刷新</el-button>
        </div>
        <el-table :data="historyList" border stripe v-loading="loading.history" empty-text="暂无历史记录">
          <el-table-column prop="submittedBy" label="提交人" width="100" />
          <el-table-column label="操作类型" width="85">
            <template #default="{ row }">
              <el-tag :type="typeTagType(row.type)" size="small" effect="light">{{ typeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="设备名称" min-width="150">
            <template #default="{ row }"><span style="font-weight:500">{{ deviceName(row) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approvedBy" label="审批人" width="100" />
          <el-table-column prop="submittedAt" label="提交时间" width="150">
            <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column prop="approvedAt" label="审批时间" width="150">
            <template #default="{ row }">{{ formatTime(row.approvedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button size="small" plain @click="openDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="historyPage"
            :page-size="20"
            :total="historyTotal"
            layout="total, prev, pager, next"
            @current-change="loadHistory"
            small
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 详情 Dialog -->
    <el-dialog v-model="detailVisible" :title="detailDialogTitle" width="min(720px, 96vw)" top="4vh" class="audit-detail-dialog">
      <div v-if="detailRecord">

        <!-- 流程状态条 -->
        <div class="flow-bar" :class="flowBarClass">
          <div class="flow-step" :class="{ done: true }">
            <div class="flow-dot done-dot">✓</div>
            <div class="flow-info">
              <div class="flow-title">提交申请</div>
              <div class="flow-sub">{{ detailRecord.submittedBy }} · {{ formatTime(detailRecord.submittedAt) }}</div>
            </div>
          </div>
          <div class="flow-line"></div>
          <div class="flow-step" :class="{ done: detailRecord.status !== 'PENDING', active: detailRecord.status === 'PENDING' }">
            <div class="flow-dot" :class="{ 'done-dot': detailRecord.status !== 'PENDING', 'active-dot': detailRecord.status === 'PENDING', 'error-dot': detailRecord.status === 'REJECTED' }">
              {{ detailRecord.status === 'PENDING' ? '…' : (detailRecord.status === 'APPROVED' ? '✓' : '✕') }}
            </div>
            <div class="flow-info">
              <div class="flow-title">管理员审核</div>
              <div class="flow-sub">
                <span v-if="detailRecord.status === 'PENDING'" style="color:#f59e0b">等待审批</span>
                <span v-else>{{ detailRecord.approvedBy }} · {{ formatTime(detailRecord.approvedAt) }}</span>
              </div>
            </div>
          </div>
          <div class="flow-line"></div>
          <div class="flow-step" :class="{ done: detailRecord.status === 'APPROVED', error: detailRecord.status === 'REJECTED' }">
            <div class="flow-dot" :class="{ 'done-dot': detailRecord.status === 'APPROVED', 'error-dot': detailRecord.status === 'REJECTED', 'pending-dot': detailRecord.status === 'PENDING' }">
              {{ detailRecord.status === 'APPROVED' ? '✓' : (detailRecord.status === 'REJECTED' ? '✕' : '—') }}
            </div>
            <div class="flow-info">
              <div class="flow-title">{{ detailRecord.status === 'REJECTED' ? '已驳回' : '执行生效' }}</div>
              <div class="flow-sub">
                <span v-if="detailRecord.status === 'PENDING'" style="color:#94a3b8">待定</span>
                <span v-else-if="detailRecord.status === 'APPROVED'" style="color:#16a34a">操作已自动执行</span>
                <span v-else style="color:#dc2626">{{ detailRecord.rejectReason || '已驳回' }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 备注 -->
        <div v-if="detailRecord.remark" class="remark-bar">
          <span class="remark-label">备注</span>{{ detailRecord.remark }}
        </div>

        <!-- 变更内容 -->
        <div class="diff-wrap">
          <div class="diff-header">
            <span class="diff-header-title">
              {{ detailRecord.type === 'UPDATE' ? '变更字段' : detailRecord.type === 'CREATE' ? '新增设备数据' : '删除的设备数据' }}
            </span>
            <span v-if="detailRecord.type === 'UPDATE'" class="diff-changed-count">
              <span v-if="diffRows.length > 0" style="color:#d97706">{{ diffRows.length }} 项已修改</span>
              <span v-else style="color:#64748b">无字段差异</span>
            </span>
          </div>

          <!-- UPDATE: 三列对比表 -->
          <template v-if="detailRecord.type === 'UPDATE'">
            <table class="diff-table">
              <colgroup>
                <col style="width:110px">
                <col>
                <col>
              </colgroup>
              <thead>
                <tr>
                  <th class="dt-th-label">字段</th>
                  <th class="dt-th-orig">修改前</th>
                  <th class="dt-th-new">修改后</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="diffRows.length === 0">
                  <td colspan="3" style="text-align:center;color:#94a3b8;padding:16px">未检测到字段变更</td>
                </tr>
                <tr v-for="r in diffRows" :key="r.key" class="dt-row-changed">
                  <td class="dt-cell-label">{{ r.label }}</td>
                  <td class="dt-cell-orig">
                    <span v-if="r.origVal !== '' && r.origVal !== null && r.origVal !== undefined" class="val-del">{{ r.origVal }}</span>
                    <span v-else class="val-empty">—</span>
                  </td>
                  <td class="dt-cell-new"><span class="val-add">{{ r.newVal }}</span></td>
                </tr>
              </tbody>
            </table>
          </template>

          <!-- CREATE: 单列表格 -->
          <template v-else-if="detailRecord.type === 'CREATE'">
            <table class="diff-table">
              <colgroup><col style="width:110px"><col></colgroup>
              <tbody>
                <tr v-for="r in diffRows" :key="r.key">
                  <td class="dt-cell-label">{{ r.label }}</td>
                  <td class="dt-cell-new"><span class="val-add">{{ r.newVal }}</span></td>
                </tr>
              </tbody>
            </table>
          </template>

          <!-- DELETE: 单列表格 -->
          <template v-else-if="detailRecord.type === 'DELETE'">
            <table class="diff-table">
              <colgroup><col style="width:110px"><col></colgroup>
              <tbody>
                <tr v-for="r in diffRows" :key="r.key">
                  <td class="dt-cell-label">{{ r.label }}</td>
                  <td class="dt-cell-orig"><span class="val-del">{{ r.origVal }}</span></td>
                </tr>
              </tbody>
            </table>
          </template>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="authStore.isAdmin && detailRecord?.status === 'PENDING'">
          <el-button type="danger" plain @click="openReject(detailRecord)">驳回</el-button>
          <el-button type="success" @click="openApproveFromDetail(detailRecord)">审批通过</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 审批 Dialog -->
    <el-dialog v-model="approveVisible" title="确认审批通过" width="420px">
      <p style="color:#374151;margin-bottom:14px">操作将立即执行，不可撤销。请确认后提交。</p>
      <el-form label-width="70px">
        <el-form-item label="审批备注">
          <el-input v-model="approveRemark" type="textarea" :rows="3" placeholder="可选填写审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="success" :loading="actionLoading" @click="doApprove">确认审批</el-button>
      </template>
    </el-dialog>

    <!-- 驳回 Dialog -->
    <el-dialog v-model="rejectVisible" title="驳回申请" width="420px">
      <el-form label-width="70px">
        <el-form-item label="驳回原因">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因（选填，提交人可见）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="actionLoading" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth.js'
import { auditApi } from '../api/index.js'

const authStore = useAuthStore()
const activeTab = ref(authStore.isAdmin ? 'pending' : 'my')

const pendingList = ref([])
const myList = ref([])
const historyList = ref([])
const historyPage = ref(1)
const historyTotal = ref(0)
const loading = ref({ pending: false, my: false, history: false })

const detailVisible = ref(false)
const detailRecord = ref(null)

const approveVisible = ref(false)
const approveRecord = ref(null)
const approveRemark = ref('')

const rejectVisible = ref(false)
const rejectRecord = ref(null)
const rejectReason = ref('')
const actionLoading = ref(false)

// ── 字段标签映射 ──────────────────────────────────
const FIELD_LABELS = {
  name: '设备名称', meteringNo: '计量编号', assetNo: '资产编号',
  serialNo: '出厂编号', model: '型号', manufacturer: '制造厂',
  dept: '部门', location: '存放位置', responsiblePerson: '责任人',
  useStatus: '使用状态', classification: 'ABC分类',
  purchaseDate: '采购日期', purchasePrice: '采购价格', serviceLife: '使用年限',
  graduationValue: '分度值', testRange: '测试范围', allowedError: '允许误差',
  calibrationCycle: '检定周期(月)', calDate: '上次校准日期', calResult: '校准结果',
  remark: '备注', imageUrl: '设备图片', certUrl: '校准证书',
}
// 系统计算字段，不应出现在 diff 中
const SKIP_FIELDS = new Set(['id', 'nextCalDate', 'nextDate', 'validity', 'daysPassed', 'metricNo'])

function fieldLabel(key) {
  return FIELD_LABELS[key] || key
}

// ── 解析原始 JSON（不过滤空值，保留完整对比） ───────
function parseRaw(json) {
  if (!json) return {}
  try { return JSON.parse(json) } catch { return {} }
}

// ── 计算 diff 行列表 ──────────────────────────────
const diffRows = computed(() => {
  if (!detailRecord.value) return []
  const type = detailRecord.value.type
  const orig = parseRaw(detailRecord.value.originalData)
  const newD = parseRaw(detailRecord.value.newData)

  if (type === 'UPDATE') {
    // 取两边字段的并集，过滤系统字段
    const allKeys = Array.from(new Set([...Object.keys(orig), ...Object.keys(newD)]))
      .filter(k => !SKIP_FIELDS.has(k))
    return allKeys
      .map(k => {
        const ov = orig[k] ?? ''
        const nv = newD[k] ?? ''
        return { key: k, label: fieldLabel(k), origVal: ov, newVal: nv, changed: String(ov) !== String(nv) }
      })
      // 只展示 newData 中明确提交了非空值、且与原值不同的字段。
      // newData 缺失或为空意味着用户未编辑该字段（不是"改成了空"），不应展示在 diff 中。
      .filter(r => {
        const nv = r.newVal
        const hasNew = nv !== null && nv !== undefined && nv !== ''
        return hasNew && r.changed
      })
  }

  if (type === 'CREATE') {
    return Object.entries(newD)
      .filter(([k, v]) => !SKIP_FIELDS.has(k) && v !== null && v !== undefined && v !== '')
      .map(([k, v]) => ({ key: k, label: fieldLabel(k), origVal: '', newVal: v, changed: false }))
  }

  if (type === 'DELETE') {
    return Object.entries(orig)
      .filter(([k, v]) => !SKIP_FIELDS.has(k) && v !== null && v !== undefined && v !== '')
      .map(([k, v]) => ({ key: k, label: fieldLabel(k), origVal: v, newVal: '', changed: false }))
  }

  return []
})

// ── 辅助 ─────────────────────────────────────────
function typeLabel(t) { return { CREATE: '新增', UPDATE: '修改', DELETE: '删除' }[t] || t }
function typeTagType(t) { return { CREATE: 'success', UPDATE: 'warning', DELETE: 'danger' }[t] || '' }
function statusLabel(s) { return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s }
function statusTagType(s) { return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] || '' }

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function deviceName(row) {
  const src = row.type === 'DELETE' ? row.originalData : row.newData
  if (!src) return '—'
  try { return JSON.parse(src).name || '—' } catch { return '—' }
}

const detailDialogTitle = computed(() => {
  if (!detailRecord.value) return '申请详情'
  const tl = { CREATE: '新增设备', UPDATE: '修改设备', DELETE: '删除设备' }[detailRecord.value.type] || '操作'
  const sl = { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' }[detailRecord.value.status] || ''
  return `${tl}申请 · ${sl}`
})

const flowBarClass = computed(() => {
  if (!detailRecord.value) return ''
  return { APPROVED: 'flow-bar-approved', REJECTED: 'flow-bar-rejected', PENDING: 'flow-bar-pending' }[detailRecord.value.status] || ''
})

// ── 数据加载 ─────────────────────────────────────
async function loadPending() {
  loading.value.pending = true
  try { const r = await auditApi.pending(); pendingList.value = r.data }
  catch { ElMessage.error('加载待审批失败') }
  finally { loading.value.pending = false }
}

async function loadMy() {
  loading.value.my = true
  try { const r = await auditApi.my(); myList.value = r.data }
  catch { ElMessage.error('加载申请记录失败') }
  finally { loading.value.my = false }
}

async function loadHistory(page) {
  if (typeof page === 'number') historyPage.value = page
  loading.value.history = true
  try {
    const r = await auditApi.all({ page: historyPage.value, size: 20 })
    const d = r.data
    historyList.value = d.content ?? d.items ?? d
    historyTotal.value = d.total ?? d.totalElements ?? historyList.value.length
  } catch { ElMessage.error('加载历史记录失败') }
  finally { loading.value.history = false }
}

function onTabChange(tab) {
  if (tab === 'pending' && pendingList.value.length === 0) loadPending()
  else if (tab === 'my' && myList.value.length === 0) loadMy()
  else if (tab === 'history' && historyList.value.length === 0) loadHistory()
}

// ── 详情 ─────────────────────────────────────────
function openDetail(row) { detailRecord.value = row; detailVisible.value = true }

// ── 审批 ─────────────────────────────────────────
function openApprove(row) { approveRecord.value = row; approveRemark.value = ''; approveVisible.value = true }
function openApproveFromDetail(row) { detailVisible.value = false; openApprove(row) }

async function doApprove() {
  if (!approveRecord.value) return
  actionLoading.value = true
  try {
    await auditApi.approve(approveRecord.value.id, { remark: approveRemark.value })
    ElMessage.success('审批通过，操作已执行')
    approveVisible.value = false
    loadPending()
    if (activeTab.value === 'history') loadHistory()
  } catch(e) { ElMessage.error(e.response?.data?.message || '审批失败') }
  finally { actionLoading.value = false }
}

// ── 驳回 ─────────────────────────────────────────
function openReject(row) { detailVisible.value = false; rejectRecord.value = row; rejectReason.value = ''; rejectVisible.value = true }

async function doReject() {
  if (!rejectRecord.value) return
  actionLoading.value = true
  try {
    await auditApi.reject(rejectRecord.value.id, { reason: rejectReason.value })
    ElMessage.success('已驳回申请')
    rejectVisible.value = false
    loadPending()
    if (activeTab.value === 'history') loadHistory()
  } catch(e) { ElMessage.error(e.response?.data?.message || '驳回失败') }
  finally { actionLoading.value = false }
}

onMounted(() => {
  if (authStore.isAdmin) loadPending()
  else loadMy()
})
</script>

<style scoped>
.audit-view { padding: 0 2px; }
.tab-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.record-count { color: #64748b; font-size: 13px; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 14px; }
.text-muted { color: #94a3b8; }

/* 流程说明（我的申请 tab） */
.workflow-hint {
  margin-top: 28px; padding: 18px 20px;
  background: #f8fafc; border-radius: 12px; border: 1px solid #e2e8f0;
}
.workflow-title { font-size: 12px; font-weight: 600; color: #64748b; margin-bottom: 14px; text-transform: uppercase; letter-spacing: .5px; }
.workflow-steps-row { display: flex; align-items: center; gap: 0; flex-wrap: wrap; }
.wf-step { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 140px; }
.wf-step-icon {
  width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; flex-shrink: 0;
}
.wf-step-done .wf-step-icon { background: #d1fae5; color: #059669; }
.wf-step-active .wf-step-icon { background: #fef3c7; color: #d97706; }
.wf-step-pending .wf-step-icon { background: #e0e7ff; color: #4338ca; }
.wf-step-label { font-size: 12px; color: #475569; line-height: 1.4; }
.wf-arrow { font-size: 18px; color: #cbd5e1; padding: 0 8px; flex-shrink: 0; }

/* 流程状态条（详情对话框） */
.flow-bar {
  display: flex; align-items: center;
  padding: 16px 20px; border-radius: 12px; margin-bottom: 14px;
  border: 1px solid #e2e8f0; background: #f8fafc;
}
.flow-bar-approved { border-color: #bbf7d0; background: #f0fdf4; }
.flow-bar-rejected { border-color: #fecaca; background: #fff5f5; }
.flow-bar-pending  { border-color: #fde68a; background: #fffbeb; }

.flow-step { display: flex; align-items: center; gap: 10px; flex: 1; }
.flow-line { height: 2px; flex: 0 0 32px; background: #e2e8f0; }
.flow-dot {
  width: 30px; height: 30px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 700; border: 2px solid transparent;
}
.done-dot    { background: #d1fae5; color: #059669; border-color: #6ee7b7; }
.active-dot  { background: #fef3c7; color: #d97706; border-color: #fcd34d; }
.error-dot   { background: #fee2e2; color: #dc2626; border-color: #fca5a5; }
.pending-dot { background: #f1f5f9; color: #94a3b8; border-color: #e2e8f0; }

.flow-info { min-width: 0; }
.flow-title { font-size: 12px; font-weight: 600; color: #374151; }
.flow-sub { font-size: 11px; color: #64748b; margin-top: 2px; word-break: break-all; }

/* 备注条 */
.remark-bar {
  padding: 8px 14px; border-radius: 8px; font-size: 13px;
  background: #eff6ff; color: #1d4ed8; margin-bottom: 12px;
}
.remark-label { font-weight: 600; margin-right: 8px; }

/* diff 区域 */
.diff-wrap {
  border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden;
}
.diff-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; background: #f8fafc; border-bottom: 1px solid #e2e8f0;
}
.diff-header-title { font-size: 13px; font-weight: 600; color: #374151; }
.diff-changed-count { font-size: 12px; }

/* diff 表格 */
.diff-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.diff-table colgroup col { }

/* 表头 */
.dt-th-label { background: #f1f5f9; padding: 8px 12px; text-align: left; font-weight: 600; color: #475569; border-bottom: 1px solid #e2e8f0; }
.dt-th-orig  { background: #fff7ed; padding: 8px 12px; text-align: left; font-weight: 600; color: #92400e; border-bottom: 1px solid #e2e8f0; border-left: 1px solid #fed7aa; }
.dt-th-new   { background: #f0fdf4; padding: 8px 12px; text-align: left; font-weight: 600; color: #14532d; border-bottom: 1px solid #e2e8f0; border-left: 1px solid #bbf7d0; }

/* 行 */
.dt-row-changed td { background: #fffbeb; }
.dt-row-same td { background: #fff; }
.dt-row-changed:hover td, .dt-row-same:hover td { background: #f8fafc !important; }

/* 单元格 */
.dt-cell-label { padding: 8px 12px; color: #64748b; font-weight: 500; border-bottom: 1px solid #f1f5f9; white-space: nowrap; }
.dt-cell-orig  { padding: 8px 12px; border-bottom: 1px solid #f1f5f9; border-left: 1px solid #fed7aa; word-break: break-all; }
.dt-cell-new   { padding: 8px 12px; border-bottom: 1px solid #f1f5f9; border-left: 1px solid #bbf7d0; word-break: break-all; }

/* 值样式 */
.val-del {
  display: inline-block; background: #fee2e2; color: #991b1b;
  border-radius: 4px; padding: 1px 6px; text-decoration: line-through;
  text-decoration-color: #dc2626;
}
.val-add {
  display: inline-block; background: #dcfce7; color: #14532d;
  border-radius: 4px; padding: 1px 6px;
}
.val-empty { color: #94a3b8; }

@media (max-width: 640px) {
  .flow-bar { flex-direction: column; align-items: flex-start; gap: 10px; }
  .flow-line { width: 2px; height: 16px; flex: none; margin-left: 14px; }
  .diff-table { font-size: 12px; }
  .wf-arrow { display: none; }
  .workflow-steps-row { gap: 8px; }
  .wf-step { flex: none; }
}
</style>
