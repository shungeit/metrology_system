import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api/index.js'

export const useAuthStore = defineStore('auth', () => {
  const token      = ref(localStorage.getItem('token') || '')
  const username   = ref(localStorage.getItem('username') || '')
  const userId     = ref(localStorage.getItem('userId') || '')
  const role       = ref(localStorage.getItem('role') || 'USER')
  const permissions = ref(JSON.parse(localStorage.getItem('permissions') || '[]'))

  const isAdmin = computed(() => role.value === 'ADMIN')
  const canView   = computed(() => isAdmin.value || permissions.value.includes('DEVICE_VIEW'))
  const canCreate = computed(() => isAdmin.value || permissions.value.includes('DEVICE_CREATE'))
  const canUpdate = computed(() => isAdmin.value || permissions.value.includes('DEVICE_UPDATE'))
  const canDelete = computed(() => isAdmin.value || permissions.value.includes('DEVICE_DELETE'))
  const canManageStatus = computed(() => isAdmin.value || permissions.value.includes('STATUS_MANAGE'))
  const canManageUsers  = computed(() => isAdmin.value || permissions.value.includes('USER_MANAGE'))
  const canAccessFiles  = computed(() => isAdmin.value || permissions.value.includes('FILE_ACCESS'))
  const canAccessWebdav = computed(() => isAdmin.value || permissions.value.includes('WEBDAV_ACCESS'))

  function setAuth(data) {
    token.value = data.token
    username.value = data.username
    userId.value = data.userId
    role.value = data.role || 'USER'
    permissions.value = data.permissions || []
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('userId', data.userId)
    localStorage.setItem('role', role.value)
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  function clearAuth() {
    token.value = ''; username.value = ''; userId.value = ''
    role.value = 'USER'; permissions.value = []
    localStorage.removeItem('token'); localStorage.removeItem('username')
    localStorage.removeItem('userId'); localStorage.removeItem('role')
    localStorage.removeItem('permissions')
  }

  async function login(credentials) {
    const res = await authApi.login(credentials)
    setAuth(res.data)
  }

  async function register(credentials) {
    const res = await authApi.register(credentials)
    setAuth(res.data)
  }

  function logout() { clearAuth() }

  function updateFromResponse(data) {
    setAuth(data)
  }

  /** 向后端刷新当前用户权限（无需重新登录），管理员更改权限后立即生效 */
  async function refreshPermissions() {
    if (!token.value) return
    try {
      const res = await authApi.me()
      if (res.data) {
        permissions.value = res.data.permissions || []
        role.value = res.data.role || 'USER'
        localStorage.setItem('permissions', JSON.stringify(permissions.value))
        localStorage.setItem('role', role.value)
      }
    } catch { /* 静默失败，不影响现有会话 */ }
  }

  return {
    token, username, userId, role, permissions,
    isAdmin, canView, canCreate, canUpdate, canDelete, canManageStatus, canManageUsers,
    canAccessFiles, canAccessWebdav,
    login, register, logout, updateFromResponse, refreshPermissions
  }
})
