import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 30000 })

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.clear()
      window.location.href = '/login'
    } else if (err.response?.status === 403) {
      // 403 may be a permission denial (not auth failure), don't redirect to login
      // Only redirect if not authenticated at all (no token)
      const token = localStorage.getItem('token')
      if (!token) {
        localStorage.clear()
        window.location.href = '/login'
      }
    } else if (!err.response) {
      console.error('网络错误，请检查网络连接')
    }
    return Promise.reject(err)
  }
)

export const authApi = {
  login: (d) => api.post('/auth/login', d),
  me: () => api.get('/auth/me'),
  changePassword: (d) => api.post('/auth/change-password', d),
  changeUsername: (d) => api.post('/auth/change-username', d),
}

export const deviceApi = {
  list: (p) => api.get('/devices', { params: p }),
  listPaged: (p) => api.get('/devices/paged', { params: p }),
  create: (d) => api.post('/devices', d),
  update: (id, d) => api.put(`/devices/${id}`, d),
  remove: (id) => api.delete(`/devices/${id}`),
  dashboard: () => api.get('/devices/dashboard'),
  export: (p) => api.get('/devices/export', { params: p, responseType: 'blob' }),
  exportCalibration: (p) => api.get('/devices/export/calibration', { params: p, responseType: 'blob' }),
  template: () => api.get('/devices/template', { responseType: 'blob' }),
  import: (file) => { const fd = new FormData(); fd.append('file', file); return api.post('/devices/import', fd) },
  uploadFile: (file) => { const fd = new FormData(); fd.append('file', file); return api.post('/devices/upload', fd) },
  batchUpdate: (ids, data) => Promise.all(ids.map(id => api.put(`/devices/${id}`, data))),
}

export const settingsApi = {
  get: () => api.get('/settings'),
  save: (d) => api.put('/settings', d),
}

export const deviceStatusApi = {
  list: () => api.get('/device-statuses'),
  create: (name) => api.post('/device-statuses', { name }),
  update: (id, name) => api.put(`/device-statuses/${id}`, { name }),
  remove: (id) => api.delete(`/device-statuses/${id}`),
}

export const userApi = {
  list: () => api.get('/users'),
  create: (d) => api.post('/users', d),
  updateRolePermissions: (id, data) => api.put(`/users/${id}/role-permissions`, data),
  remove: (id) => api.delete(`/users/${id}`),
}

export const fileApi = {
  list: (parentId) => api.get('/files', { params: parentId != null ? { parentId } : {} }),
  search: (q) => api.get('/files/search', { params: { q } }),
  breadcrumb: (folderId) => api.get('/files/breadcrumb', { params: { folderId } }),
  createFolder: (name, parentId) => api.post('/files/folder', { name, parentId: parentId ?? null }),
  upload: (file, parentId) => {
    const fd = new FormData(); fd.append('file', file)
    return api.post('/files/upload', fd, { params: parentId != null ? { parentId } : {} })
  },
  download: (id) => api.get(`/files/${id}/download`, { responseType: 'blob' }),
  delete: (id) => api.delete(`/files/${id}`),
  rename: (id, name) => api.put(`/files/${id}/rename`, { name }),
}

export const webDavApi = {
  listMounts: () => api.get('/webdav/mounts'),
  createMount: (d) => api.post('/webdav/mounts', d),
  updateMount: (id, d) => api.put(`/webdav/mounts/${id}`, d),
  deleteMount: (id) => api.delete(`/webdav/mounts/${id}`),
  testConnection: (d) => api.post('/webdav/mounts/test', d),
  browse: (mountId, path) => api.get('/webdav/browse', { params: { mountId, path } }),
  download: (mountId, path, filename) => api.get('/webdav/download', { params: { mountId, path, filename }, responseType: 'blob' }),
  upload: (mountId, path, file) => {
    const fd = new FormData(); fd.append('file', file)
    return api.post('/webdav/upload', fd, { params: { mountId, path } })
  },
}

export const deptApi = {
  list: (search) => api.get('/departments', { params: search ? { search } : {} }),
  tree: () => api.get('/departments/tree'),
  create: (d) => api.post('/departments', d),
  update: (id, d) => api.put(`/departments/${id}`, d),
  remove: (id) => api.delete(`/departments/${id}`),
  export: (search) => api.get('/departments/export', { params: search ? { search } : {}, responseType: 'blob' }),
  exportAll: () => api.get('/departments/export/all', { responseType: 'blob' }),
  template: () => api.get('/departments/template', { responseType: 'blob' }),
  import: (file) => { const fd = new FormData(); fd.append('file', file); return api.post('/departments/import', fd) },
}

export const auditApi = {
  pending: () => api.get('/audit/pending'),
  my: () => api.get('/audit/my'),
  all: (p) => api.get('/audit', { params: p }),
  get: (id) => api.get(`/audit/${id}`),
  approve: (id, data) => api.post(`/audit/${id}/approve`, data),
  reject: (id, data) => api.post(`/audit/${id}/reject`, data),
  workflow: () => api.get('/audit/workflow'),
  saveWorkflow: (steps) => api.put('/audit/workflow', steps),
}

export default api
