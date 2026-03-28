<template>
  <div class="settings-max" style="max-width:640px;display:flex;flex-direction:column;gap:20px">

    <div class="card">
      <div class="card-title">🔐 账号安全</div>
      <div style="display:flex;flex-direction:column;gap:14px">
        <div class="form-group">
          <label class="form-label">当前用户名</label>
          <input :value="authStore.username" class="input-readonly" readonly style="width:100%" />
        </div>
        <div class="form-group">
          <label class="form-label">修改用户名</label>
          <input v-model="newUsername" placeholder="输入新用户名（留空则不修改）" style="width:100%" />
        </div>
        <div style="height:1px;background:var(--border)"></div>
        <div class="form-group">
          <label class="form-label">当前密码</label>
          <input v-model="oldPw" type="password" placeholder="输入当前密码" style="width:100%" />
        </div>
        <div class="form-group">
          <label class="form-label">新密码</label>
          <input v-model="newPw" type="password" placeholder="输入新密码（留空则不修改）" style="width:100%" />
        </div>
        <div class="form-group">
          <label class="form-label">确认新密码</label>
          <input v-model="confirmPw" type="password" placeholder="再次输入新密码" style="width:100%" />
        </div>
        <div>
          <button class="btn btn-primary" @click="updateAccount">保存账号信息</button>
        </div>
      </div>
    </div>

    <div v-if="authStore.isAdmin" class="card">
      <div class="card-title">⚙️ 校准参数</div>
      <div style="display:flex;flex-direction:column;gap:14px">
        <div class="form-group">
          <label class="form-label">即将过期预警阈值（天）</label>
          <input v-model.number="settings.warningDays" type="number" min="1" style="width:100%" />
          <span class="form-hint">距离失效日期少于此天数时，标记为「即将过期」</span>
        </div>
        <div class="form-group">
          <label class="form-label">失效判定阈值（天）</label>
          <input v-model.number="settings.expiredDays" type="number" min="1" style="width:100%" />
          <span class="form-hint">校准日期超过此天数后，标记为「失效」</span>
        </div>
        <div>
          <button class="btn btn-primary" @click="saveSettings">保存配置</button>
        </div>
      </div>
    </div>

    <div class="card" style="background:linear-gradient(135deg,#eff6ff,#f0fdf4)">
      <div style="font-size:13.5px;color:var(--text-muted)">
        <div style="font-weight:700;color:var(--text);margin-bottom:8px">💡 系统信息</div>
        <div>当前登录：<b>{{ authStore.username }}</b></div>
        <div>账号角色：<b>{{ authStore.isAdmin ? '管理员' : '普通用户' }}</b></div>
        <div style="margin-top:8px">如需管理用户权限，请前往「用户管理」页面</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { authApi, settingsApi } from '../api/index.js'

const showToast = inject('showToast')
const router = useRouter()
const authStore = useAuthStore()
const newUsername = ref(''), oldPw = ref(''), newPw = ref(''), confirmPw = ref('')
const settings = reactive({ warningDays: 315, expiredDays: 360 })

async function updateAccount() {
  const nu = newUsername.value.trim()
  if (!oldPw.value) { showToast('请输入当前密码','error'); return }
  if (newPw.value && newPw.value !== confirmPw.value) { showToast('两次输入的密码不一致','error'); return }
  try {
    if (newPw.value) {
      await authApi.changePassword({ oldPassword: oldPw.value, newPassword: newPw.value })
      showToast('密码修改成功')
    }
    if (nu && nu !== authStore.username) {
      const res = await authApi.changeUsername({ newUsername: nu })
      authStore.updateFromResponse(res.data)
      showToast('用户名已修改，请重新登录')
      setTimeout(() => { authStore.logout(); router.push('/login') }, 1800)
      return
    }
    oldPw.value=''; newPw.value=''; confirmPw.value=''; newUsername.value=''
  } catch(e) { showToast(e.response?.data?.message||'操作失败','error') }
}

async function saveSettings() {
  try {
    await settingsApi.save({ warningDays: settings.warningDays, expiredDays: settings.expiredDays })
    showToast('配置已保存，有效性重新计算完成')
  } catch(e) { showToast('保存失败','error') }
}

onMounted(async () => {
  try { const r = await settingsApi.get(); settings.warningDays=r.data.warningDays; settings.expiredDays=r.data.expiredDays } catch(e) {}
})
</script>
