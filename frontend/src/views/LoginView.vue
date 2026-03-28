<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-blob b1"></div>
      <div class="bg-blob b2"></div>
      <div class="bg-blob b3"></div>
    </div>

    <div class="login-card">
      <div class="login-brand">
        <div class="brand-icon">📐</div>
        <div class="brand-text">
          <div class="brand-name">计量管理系统</div>
          <div class="brand-sub">Metrology Management System</div>
        </div>
      </div>

      <form @submit.prevent="submit" class="login-form">
        <div class="field">
          <label class="field-label">用户名</label>
          <div class="field-wrap">
            <svg class="field-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            <input v-model="form.username" type="text" placeholder="请输入用户名"
                   required autocomplete="username" class="field-input" />
          </div>
        </div>

        <div class="field">
          <label class="field-label">密码</label>
          <div class="field-wrap">
            <svg class="field-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            <input v-model="form.password" :type="showPw ? 'text' : 'password'"
                   placeholder="请输入密码" required autocomplete="current-password" class="field-input" />
            <button type="button" class="pw-toggle" @click="showPw = !showPw" tabindex="-1">
              <svg v-if="!showPw" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg v-else width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </div>

        <div v-if="error" class="error-msg">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          {{ error }}
        </div>

        <button type="submit" class="submit-btn" :disabled="loading">
          <span v-if="loading" class="spinner"></span>
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>

      <div class="login-footer-hint">如需注册账号，请联系管理员</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const showPw = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  error.value = ''
  if (!form.username.trim()) { error.value = '请输入用户名'; return }
  if (!form.password) { error.value = '请输入密码'; return }
  loading.value = true
  try {
    await authStore.login({ username: form.username, password: form.password })
    router.push('/dashboard')
  } catch (e) {
    error.value = e.response?.data?.message || '用户名或密码错误'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh; width: 100%;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(145deg, #0f172a 0%, #1e3a5f 55%, #0f172a 100%);
  padding: 20px; position: relative; overflow: hidden;
}

/* Background blobs */
.login-bg { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.bg-blob {
  position: absolute; border-radius: 50%;
  filter: blur(60px);
}
.b1 { width: 500px; height: 500px; top: -150px; left: -150px; background: rgba(37,99,235,0.12); }
.b2 { width: 400px; height: 400px; bottom: -100px; right: -80px; background: rgba(124,58,237,0.12); }
.b3 { width: 280px; height: 280px; top: 40%; left: 55%; background: rgba(37,99,235,0.07); }

/* Card */
.login-card {
  background: rgba(255,255,255,0.98);
  border-radius: 20px; padding: 40px;
  width: 100%; max-width: 420px;
  position: relative; z-index: 1;
  box-shadow: 0 24px 64px rgba(0,0,0,0.45), 0 4px 16px rgba(0,0,0,0.15);
}

/* Brand */
.login-brand {
  display: flex; align-items: center; gap: 14px; margin-bottom: 32px;
}
.brand-icon {
  width: 50px; height: 50px; border-radius: 14px; flex-shrink: 0;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  display: flex; align-items: center; justify-content: center; font-size: 24px;
  box-shadow: 0 6px 16px rgba(37,99,235,0.35);
}
.brand-name { font-size: 18px; font-weight: 800; color: #0f172a; letter-spacing: -0.3px; }
.brand-sub  { font-size: 11.5px; color: #64748b; margin-top: 2px; }

/* Form */
.login-form { display: flex; flex-direction: column; gap: 16px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field-label { font-size: 13px; font-weight: 700; color: #374151; }
.field-wrap { position: relative; }
.field-icon {
  position: absolute; left: 12px; top: 50%; transform: translateY(-50%);
  color: #94a3b8; pointer-events: none; flex-shrink: 0;
}
.field-input {
  width: 100%; padding: 11px 42px 11px 40px;
  border: 1.5px solid #e2e8f0; border-radius: 11px;
  font-size: 14px; color: #0f172a; background: white;
  transition: border-color 0.18s, box-shadow 0.18s;
  font-family: inherit; -webkit-appearance: none; outline: none;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}
.field-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37,99,235,0.13);
}
.field-input::placeholder { color: #94a3b8; }
.pw-toggle {
  position: absolute; right: 12px; top: 50%; transform: translateY(-50%);
  background: none; border: none; cursor: pointer; padding: 3px;
  color: #94a3b8; display: flex; align-items: center; transition: color 0.15s;
}
.pw-toggle:hover { color: #475569; }

/* Error */
.error-msg {
  display: flex; align-items: center; gap: 8px;
  background: #fef2f2; border: 1px solid #fecaca; color: #991b1b;
  padding: 10px 14px; border-radius: 10px; font-size: 13px;
}

/* Submit */
.submit-btn {
  padding: 13px; margin-top: 4px; border-radius: 12px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: white; border: none; font-size: 15px; font-weight: 700;
  cursor: pointer; transition: all 0.2s;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  letter-spacing: 0.5px;
  box-shadow: 0 4px 14px rgba(37,99,235,0.35);
}
.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(37,99,235,0.45);
}
.submit-btn:active:not(:disabled) { transform: translateY(0); }
.submit-btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }

/* Spinner */
.spinner {
  width: 17px; height: 17px; border-radius: 50%;
  border: 2.5px solid rgba(255,255,255,0.3);
  border-top-color: white;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Footer hint */
.login-footer-hint {
  margin-top: 20px; text-align: center;
  font-size: 12px; color: #94a3b8;
}

/* ===== Mobile ===== */
@media (max-width: 480px) {
  .login-page {
    padding: 0;
    align-items: flex-end;
  }
  .login-card {
    border-radius: 22px 22px 0 0;
    padding: 28px 20px 36px;
    max-width: 100%;
    box-shadow: 0 -8px 40px rgba(0,0,0,0.3);
    max-height: 96vh;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }
  .login-brand { margin-bottom: 24px; }
  .brand-icon { width: 44px; height: 44px; font-size: 20px; }
  .brand-name { font-size: 16px; }
}

@media (max-width: 360px) {
  .login-card { padding: 24px 16px 32px; }
}
</style>
