<template>
  <div class="app-shell" :class="{ 'sidebar-collapsed': collapsed }">

    <!-- Mobile sidebar overlay -->
    <div v-if="mobileOpen" class="sidebar-overlay" @click="mobileOpen=false"></div>

    <aside :class="{ 'sidebar-mobile-open': mobileOpen }">
      <div class="logo">
        <div class="logo-icon">📐</div>
        <span class="logo-text">计量管理</span>
        <button class="sidebar-close-btn" @click="mobileOpen=false" title="关闭">✕</button>
      </div>

      <nav class="nav">
        <router-link to="/dashboard" :class="['nav-item', route.path==='/dashboard'?'active':'']" @click="mobileOpen=false" data-tip="总览看板">
          <span class="nav-icon">📊</span>
          <span class="nav-label">总览看板</span>
        </router-link>
        <router-link to="/equipment" :class="['nav-item', route.path==='/equipment'?'active':'']" @click="mobileOpen=false" data-tip="设备台账">
          <span class="nav-icon">🔧</span>
          <span class="nav-label">设备台账</span>
        </router-link>
        <router-link to="/device-status" :class="['nav-item', route.path==='/device-status'?'active':'']" @click="mobileOpen=false" data-tip="使用状态">
          <span class="nav-icon">🔴</span>
          <span class="nav-label">使用状态</span>
        </router-link>
        <router-link to="/calibration" :class="['nav-item', route.path==='/calibration'?'active':'']" @click="mobileOpen=false" data-tip="校准管理">
          <span class="nav-icon">📅</span>
          <span class="nav-label">校准管理</span>
        </router-link>
        <router-link to="/todo" :class="['nav-item', route.path==='/todo'?'active':'']" @click="mobileOpen=false" data-tip="我的待办">
          <span class="nav-icon">✅</span>
          <span class="nav-label">我的待办</span>
        </router-link>
        <router-link v-if="authStore.canAccessFiles" to="/files" :class="['nav-item', route.path==='/files'?'active':'']" @click="mobileOpen=false" data-tip="我的文件">
          <span class="nav-icon">📁</span>
          <span class="nav-label">我的文件</span>
        </router-link>
        <router-link v-if="authStore.canAccessWebdav" to="/webdav" :class="['nav-item', route.path==='/webdav'?'active':'']" @click="mobileOpen=false" data-tip="网络挂载">
          <span class="nav-icon">🌐</span>
          <span class="nav-label">网络挂载</span>
        </router-link>
        <router-link v-if="authStore.isAdmin" to="/departments" :class="['nav-item', route.path==='/departments'?'active':'']" @click="mobileOpen=false" data-tip="部门管理">
          <span class="nav-icon">🏢</span>
          <span class="nav-label">部门管理</span>
        </router-link>
        <router-link v-if="authStore.canManageUsers" to="/users" :class="['nav-item', route.path==='/users'?'active':'']" @click="mobileOpen=false" data-tip="用户管理">
          <span class="nav-icon">👥</span>
          <span class="nav-label">用户管理</span>
        </router-link>
        <router-link to="/audit" :class="['nav-item', route.path==='/audit'?'active':'']" @click="mobileOpen=false; loadPendingCount()" data-tip="数据审核">
          <span class="nav-icon">📋</span>
          <span class="nav-label">数据审核</span>
          <span v-if="pendingAuditCount > 0 && authStore.isAdmin" class="nav-badge">{{ pendingAuditCount }}</span>
        </router-link>
        <router-link to="/settings" :class="['nav-item', route.path==='/settings'?'active':'']" @click="mobileOpen=false" data-tip="系统设置">
          <span class="nav-icon">⚙️</span>
          <span class="nav-label">系统设置</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="user-info">
          <div class="user-avatar">{{ authStore.username.charAt(0).toUpperCase() }}</div>
          <div class="user-info-text">
            <div class="user-name">{{ authStore.username }}</div>
            <div class="user-role">{{ authStore.isAdmin ? '管理员' : '普通用户' }}</div>
          </div>
        </div>
        <button class="btn-logout" @click="handleLogout">
          <span class="logout-icon">🚪</span>
          <span class="logout-text">退出登录</span>
        </button>
      </div>

      <!-- PC collapse toggle button -->
      <button class="sidebar-collapse-btn" @click="toggleCollapse" :title="collapsed ? '展开侧边栏' : '收起侧边栏'">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline v-if="!collapsed" points="15 18 9 12 15 6"></polyline>
          <polyline v-else points="9 18 15 12 9 6"></polyline>
        </svg>
      </button>
    </aside>

    <main>
      <div class="topbar">
        <div class="topbar-left">
          <button class="hamburger-btn" @click="mobileOpen=true" title="菜单">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <div class="page-title">{{ pageTitle }}</div>
        </div>
        <div class="topbar-right">
          <button class="refresh-btn" @click="handleRefresh" title="刷新页面缓存">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
          </button>
          <span class="topbar-date">{{ today }}</span>
        </div>
      </div>
      <div class="content">
        <router-view />
      </div>
    </main>

    <!-- Mobile bottom navigation -->
    <nav class="mobile-bottom-nav">
      <router-link to="/dashboard" class="mbn-item" :class="{ active: route.path==='/dashboard' }">
        <span class="mbn-icon">📊</span>
        <span class="mbn-label">总览</span>
      </router-link>
      <router-link to="/equipment" class="mbn-item" :class="{ active: route.path==='/equipment' }">
        <span class="mbn-icon">🔧</span>
        <span class="mbn-label">设备</span>
      </router-link>
      <router-link to="/calibration" class="mbn-item" :class="{ active: route.path==='/calibration' }">
        <span class="mbn-icon">📅</span>
        <span class="mbn-label">校准</span>
      </router-link>
      <router-link to="/todo" class="mbn-item" :class="{ active: route.path==='/todo' }">
        <span class="mbn-icon">✅</span>
        <span class="mbn-label">待办</span>
      </router-link>
      <button class="mbn-item" @click="mobileOpen=true">
        <span class="mbn-icon">☰</span>
        <span class="mbn-label">更多</span>
      </button>
    </nav>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { auditApi } from '../api/index.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const mobileOpen = ref(false)
const collapsed = ref(false)
const pendingAuditCount = ref(0)
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })

const TITLES = {
  '/dashboard': '总览看板',
  '/equipment': '设备台账',
  '/device-status': '使用状态管理',
  '/calibration': '校准管理',
  '/todo': '我的待办',
  '/files': '我的文件',
  '/webdav': '网络挂载',
  '/departments': '部门管理',
  '/users': '用户管理',
  '/settings': '系统设置',
  '/audit': '数据审核',
}
const pageTitle = computed(() => TITLES[route.path] || '计量管理系统')

function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem('sidebar-collapsed', collapsed.value ? '1' : '0')
}

function handleRefresh() {
  // 清除非认证缓存并强制刷新
  const token = localStorage.getItem('token')
  const username = localStorage.getItem('username')
  const userId = localStorage.getItem('userId')
  const role = localStorage.getItem('role')
  const permissions = localStorage.getItem('permissions')
  const sidebarState = localStorage.getItem('sidebar-collapsed')
  localStorage.clear()
  if (token) localStorage.setItem('token', token)
  if (username) localStorage.setItem('username', username)
  if (userId) localStorage.setItem('userId', userId)
  if (role) localStorage.setItem('role', role)
  if (permissions) localStorage.setItem('permissions', permissions)
  if (sidebarState) localStorage.setItem('sidebar-collapsed', sidebarState)
  window.location.reload()
}

function handleLogout() {
  if (confirm('确定要退出登录吗？')) {
    authStore.logout()
    router.push('/login')
  }
}

async function loadPendingCount() {
  if (!authStore.isAdmin) return
  try {
    const r = await auditApi.pending()
    pendingAuditCount.value = r.data.length
  } catch(e) {}
}

onMounted(() => {
  collapsed.value = localStorage.getItem('sidebar-collapsed') === '1'
  loadPendingCount()
})
</script>

<style scoped>
.app-shell {
  display: flex; width: 100%; height: 100vh; overflow: hidden;
  --sidebar-w: 240px;
  --sidebar-w-collapsed: 64px;
}

/* Sidebar collapse toggle button (PC only) */
.sidebar-collapse-btn {
  display: flex; align-items: center; justify-content: center;
  width: 100%; height: 40px;
  border: none; background: transparent; cursor: pointer;
  color: #94a3b8; border-top: 1px solid #f1f5f9;
  transition: color 0.18s, background 0.18s;
  flex-shrink: 0;
}
.sidebar-collapse-btn:hover { background: #f8fafc; color: #2563eb; }

/* nav-label: hidden when collapsed */
.nav-label { transition: opacity 0.2s, width 0.2s; white-space: nowrap; }

/* user-info-text */
.user-info-text { overflow: hidden; transition: opacity 0.2s; }
.logout-text { transition: opacity 0.2s; white-space: nowrap; }
.logout-icon { flex-shrink: 0; }

/* Collapsed state (PC) */
.app-shell.sidebar-collapsed :deep(aside) {
  width: var(--sidebar-w-collapsed);
}
.app-shell.sidebar-collapsed :deep(.logo-text)      { display: none; }
.app-shell.sidebar-collapsed :deep(.nav-label)      { display: none; }
.app-shell.sidebar-collapsed :deep(.nav-item)       { justify-content: center; padding: 10px; }
.app-shell.sidebar-collapsed :deep(.nav-badge)      { display: none; }
.app-shell.sidebar-collapsed :deep(.user-info-text) { display: none; }
.app-shell.sidebar-collapsed :deep(.user-info)      { justify-content: center; padding: 8px; }
.app-shell.sidebar-collapsed :deep(.logout-text)    { display: none; }
.app-shell.sidebar-collapsed :deep(.btn-logout)     { justify-content: center; }
.app-shell.sidebar-collapsed :deep(.sidebar-footer) { padding: 8px 6px 0; }

@media (max-width: 768px) {
  .sidebar-collapse-btn { display: none; }
}

/* Refresh button */
.refresh-btn {
  display: flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; border-radius: 8px;
  border: 1px solid var(--border); background: transparent;
  color: var(--text-muted); cursor: pointer; transition: all 0.18s;
  flex-shrink: 0;
}
.refresh-btn:hover { background: var(--primary-light); color: var(--primary); border-color: var(--primary); }
</style>
