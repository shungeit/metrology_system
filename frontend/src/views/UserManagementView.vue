<template>
  <div>
    <div class="toolbar">
      <div class="toolbar-left">
        <span style="font-size:13px;color:var(--text-muted)">管理系统用户账号和权限</span>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="openCreate">+ 创建用户</el-button>
      </div>
    </div>

    <div class="table-wrap">
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>用户名</th><th>部门</th><th>角色</th><th>权限</th><th>注册时间</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="users.length===0" class="empty-row"><td colspan="6">暂无用户数据</td></tr>
            <tr v-for="u in users" :key="u.id">
              <td>
                <div style="display:flex;align-items:center;gap:8px">
                  <div class="user-avatar-sm">{{ u.username.charAt(0).toUpperCase() }}</div>
                  <span style="font-weight:500">{{ u.username }}</span>
                  <span v-if="u.username === authStore.username" class="tag tag-blue" style="font-size:11px">我</span>
                </div>
              </td>
              <td><span style="font-size:13px;color:var(--text-muted)">{{ u.department || '-' }}</span></td>
              <td>
                <span :class="['tag', u.role==='ADMIN' ? 'tag-purple' : 'tag-gray']">
                  {{ u.role === 'ADMIN' ? '👑 管理员' : '👤 普通用户' }}
                </span>
              </td>
              <td>
                <div v-if="u.role==='ADMIN'" class="text-muted text-sm">拥有所有权限</div>
                <div v-else style="display:flex;flex-wrap:wrap;gap:3px">
                  <span v-for="p in ALL_PERMS" :key="p.code"
                        :class="['perm-badge', u.permissions.includes(p.code) ? 'perm-on' : 'perm-off']">
                    {{ p.label }}
                  </span>
                  <span v-if="u.permissions.length===0" class="text-muted text-sm">暂无权限</span>
                </div>
              </td>
              <td><span class="text-sm text-muted">{{ formatDate(u.createdAt) }}</span></td>
              <td>
                <div class="action-group">
                  <button class="action-btn action-btn-edit" @click="openEdit(u)">权限设置</button>
                  <button v-if="u.username !== authStore.username" class="action-btn action-btn-del" @click="deleteUser(u.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Mobile card list -->
    <div class="mobile-list">
      <div v-if="users.length===0" style="text-align:center;padding:48px 0;color:var(--text-muted)">暂无用户数据</div>
      <div v-for="u in users" :key="u.id" class="m-card">
        <div class="m-card-row">
          <div style="display:flex;align-items:center;gap:10px;flex:1">
            <div class="user-avatar-sm">{{ u.username.charAt(0).toUpperCase() }}</div>
            <div class="m-card-title">{{ u.username }}</div>
            <span v-if="u.username === authStore.username" class="tag tag-blue" style="font-size:11px;flex-shrink:0">我</span>
          </div>
          <span :class="['tag', u.role==='ADMIN' ? 'tag-purple' : 'tag-gray']" style="flex-shrink:0">
            {{ u.role === 'ADMIN' ? '👑 管理员' : '👤 用户' }}
          </span>
        </div>
        <div class="m-card-meta">
          <div class="m-card-meta-item">部门 <b>{{ u.department || '-' }}</b></div>
          <div class="m-card-meta-item">注册时间 <b>{{ formatDate(u.createdAt) }}</b></div>
        </div>
        <div style="margin-bottom:10px">
          <div v-if="u.role==='ADMIN'" class="text-muted text-sm">拥有所有权限</div>
          <div v-else style="display:flex;flex-wrap:wrap;gap:4px">
            <span v-for="p in ALL_PERMS" :key="p.code"
                  :class="['perm-badge', u.permissions.includes(p.code) ? 'perm-on' : 'perm-off']">
              {{ p.label }}
            </span>
            <span v-if="u.permissions.length===0" class="text-muted text-sm">暂无权限</span>
          </div>
        </div>
        <div class="m-card-footer">
          <div></div>
          <div class="m-card-actions">
            <button class="action-btn action-btn-edit" @click="openEdit(u)">权限设置</button>
            <button v-if="u.username !== authStore.username" class="action-btn action-btn-del" @click="deleteUser(u.id)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Create User Modal -->
    <div v-if="showCreateModal" class="modal-mask" @click.self="closeCreate">
      <div class="modal-box modal-md">
        <div class="modal-header">
          <div class="modal-title">创建新用户</div>
          <button class="modal-close" @click="closeCreate">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group" style="margin-bottom:16px">
            <label class="form-label">用户名 <span style="color:red">*</span></label>
            <input class="form-input" v-model="createForm.username" placeholder="请输入用户名" />
          </div>
          <div class="form-group" style="margin-bottom:16px">
            <label class="form-label">密码 <span style="color:red">*</span></label>
            <div style="position:relative">
              <input class="form-input" :type="showCreatePw ? 'text' : 'password'" v-model="createForm.password" placeholder="请输入密码（至少6位）" style="padding-right:40px" />
              <span @click="showCreatePw=!showCreatePw" style="position:absolute;right:12px;top:50%;transform:translateY(-50%);cursor:pointer;font-size:14px">{{ showCreatePw ? '🙈' : '👁' }}</span>
            </div>
          </div>
          <div class="form-group" style="margin-bottom:16px">
            <label class="form-label">所属部门</label>
            <el-select v-model="createForm.department" placeholder="选择部门（可选）" clearable style="width:100%">
              <el-option v-for="d in depts" :key="d.id" :value="d.name" :label="d.name" />
            </el-select>
          </div>
          <div class="form-group">
            <label class="form-label">账号角色</label>
            <div style="display:flex;gap:10px;margin-top:8px">
              <label class="role-option" :class="{ active: createForm.role==='ADMIN' }" @click="createForm.role='ADMIN'">
                <span>👑</span> 管理员
                <small>拥有所有权限</small>
              </label>
              <label class="role-option" :class="{ active: createForm.role==='USER' }" @click="createForm.role='USER'">
                <span>👤</span> 普通用户
                <small>自定义权限</small>
              </label>
            </div>
          </div>
          <div v-if="createForm.role === 'USER'" class="form-group" style="margin-top:16px">
            <label class="form-label">功能权限</label>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:8px">
              <label v-for="p in ALL_PERMS" :key="p.code" class="perm-option" :class="{ active: createForm.permissions.includes(p.code) }">
                <input type="checkbox" :value="p.code" v-model="createForm.permissions" style="display:none" />
                <span class="perm-check">{{ createForm.permissions.includes(p.code) ? '✓' : '' }}</span>
                <div>
                  <div style="font-weight:600;font-size:13.5px">{{ p.label }}</div>
                  <div style="font-size:12px;color:var(--text-muted);margin-top:2px">{{ p.desc }}</div>
                </div>
              </label>
            </div>
          </div>
          <div v-if="createError" style="margin-top:14px;padding:10px 14px;background:#fef2f2;border:1px solid #fecaca;color:#991b1b;border-radius:8px;font-size:13px">⚠ {{ createError }}</div>
        </div>
        <div class="modal-footer">
          <el-button @click="closeCreate">取消</el-button>
          <el-button type="primary" :loading="creating" @click="submitCreate">创建用户</el-button>
        </div>
      </div>
    </div>

    <!-- Edit Modal -->
    <div v-if="showModal" class="modal-mask" @click.self="closeModal">
      <div class="modal-box modal-md">
        <div class="modal-header">
          <div class="modal-title">权限设置 — {{ editForm.username }}</div>
          <button class="modal-close" @click="closeModal">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group" style="margin-bottom:16px">
            <label class="form-label">所属部门</label>
            <el-select v-model="editForm.department" placeholder="选择部门（可选）" clearable style="width:100%">
              <el-option v-for="d in depts" :key="d.id" :value="d.name" :label="d.name" />
            </el-select>
          </div>
          <div class="form-group" style="margin-bottom:20px">
            <label class="form-label">账号角色</label>
            <div style="display:flex;gap:10px;margin-top:8px">
              <label class="role-option" :class="{ active: editForm.role==='ADMIN' }" @click="editForm.role='ADMIN'">
                <span>👑</span> 管理员
                <small>拥有所有权限</small>
              </label>
              <label class="role-option" :class="{ active: editForm.role==='USER' }" @click="editForm.role='USER'">
                <span>👤</span> 普通用户
                <small>自定义权限</small>
              </label>
            </div>
          </div>

          <div v-if="editForm.role === 'USER'" class="form-group">
            <label class="form-label">功能权限</label>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:8px">
              <label v-for="p in ALL_PERMS" :key="p.code" class="perm-option" :class="{ active: editForm.permissions.includes(p.code) }">
                <input type="checkbox" :value="p.code" v-model="editForm.permissions" style="display:none" />
                <span class="perm-check">{{ editForm.permissions.includes(p.code) ? '✓' : '' }}</span>
                <div>
                  <div style="font-weight:600;font-size:13.5px">{{ p.label }}</div>
                  <div style="font-size:12px;color:var(--text-muted);margin-top:2px">{{ p.desc }}</div>
                </div>
              </label>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <el-button @click="closeModal">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveEdit">保存权限</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, inject } from 'vue'
import { userApi, deptApi } from '../api/index.js'
import { useAuthStore } from '../stores/auth.js'

const showToast = inject('showToast')
const authStore = useAuthStore()
const users = ref([])
const depts = ref([])
const showModal = ref(false)
const saving = ref(false)
const editForm = reactive({ id: null, username: '', role: 'USER', permissions: [], department: '' })

const showCreateModal = ref(false)
const creating = ref(false)
const createError = ref('')
const showCreatePw = ref(false)
const createForm = reactive({ username: '', password: '', role: 'USER', permissions: [], department: '' })

const ALL_PERMS = [
  { code: 'DEVICE_VIEW',   label: '查看设备', desc: '查看设备列表和详情' },
  { code: 'DEVICE_CREATE', label: '新增设备', desc: '添加新设备记录' },
  { code: 'DEVICE_UPDATE', label: '修改设备', desc: '编辑设备信息' },
  { code: 'DEVICE_DELETE', label: '删除设备', desc: '删除设备记录' },
  { code: 'STATUS_MANAGE', label: '状态管理', desc: '管理设备使用状态' },
  { code: 'FILE_ACCESS',   label: '文件访问', desc: '访问我的文件模块' },
  { code: 'WEBDAV_ACCESS', label: '网络挂载', desc: '访问WebDAV挂载功能' },
]

function formatDate(s) {
  if (!s) return '-'
  return s.split('T')[0]
}

async function load() {
  try { users.value = (await userApi.list()).data } catch(e) { console.error(e) }
}

async function loadDepts() {
  try { const r = await deptApi.list(); depts.value = r.data } catch(e) {}
}

function openCreate() {
  createForm.username = ''
  createForm.password = ''
  createForm.role = 'USER'
  createForm.permissions = []
  createForm.department = ''
  createError.value = ''
  showCreatePw.value = false
  showCreateModal.value = true
}
function closeCreate() { showCreateModal.value = false }

async function submitCreate() {
  createError.value = ''
  if (!createForm.username.trim()) { createError.value = '请输入用户名'; return }
  if (!createForm.password || createForm.password.length < 6) { createError.value = '密码至少6位'; return }
  creating.value = true
  try {
    await userApi.create({
      username: createForm.username.trim(),
      password: createForm.password,
      role: createForm.role,
      department: createForm.department || null,
      permissions: createForm.role === 'ADMIN' ? [] : createForm.permissions
    })
    showToast('用户创建成功')
    closeCreate()
    load()
  } catch(e) {
    createError.value = e.response?.data?.message || '创建失败'
  } finally {
    creating.value = false
  }
}

function openEdit(u) {
  editForm.id = u.id
  editForm.username = u.username
  editForm.role = u.role || 'USER'
  editForm.permissions = [...(u.permissions || [])]
  editForm.department = u.department || ''
  showModal.value = true
}
function closeModal() { showModal.value = false }

async function saveEdit() {
  saving.value = true
  try {
    await userApi.updateRolePermissions(editForm.id, {
      role: editForm.role,
      department: editForm.department || null,
      permissions: editForm.role === 'ADMIN' ? [] : editForm.permissions
    })
    showToast('权限已更新'); closeModal(); load()
  } catch(e) { showToast(e.response?.data?.message||'更新失败','error') }
  finally { saving.value = false }
}

async function deleteUser(id) {
  if (!confirm('确定要删除该用户吗？此操作不可撤销。')) return
  try { await userApi.remove(id); showToast('用户已删除'); load() }
  catch(e) { showToast(e.response?.data?.message||'删除失败','error') }
}

onMounted(() => { load(); loadDepts() })
</script>

<style scoped>
.user-avatar-sm {
  width: 28px; height: 28px; border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; color: white; flex-shrink: 0;
}
.role-option {
  flex: 1; padding: 14px 16px; border-radius: 10px;
  border: 2px solid var(--border); cursor: pointer;
  display: flex; flex-direction: column; gap: 3px; transition: all 0.2s;
}
.role-option span { font-size: 20px; }
.role-option > :nth-child(2) { font-size: 14px; font-weight: 700; }
.role-option small { font-size: 12px; color: var(--text-muted); }
.role-option.active { border-color: var(--primary); background: var(--primary-light); }
.perm-option {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 12px; border-radius: 8px;
  border: 1.5px solid var(--border); cursor: pointer; transition: all 0.2s;
}
.perm-option.active { border-color: var(--primary); background: var(--primary-light); }
.perm-check {
  width: 18px; height: 18px; border-radius: 4px;
  border: 2px solid var(--border); flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; margin-top: 1px;
  background: white; color: var(--primary);
}
.perm-option.active .perm-check { border-color: var(--primary); background: var(--primary); color: white; }
</style>
