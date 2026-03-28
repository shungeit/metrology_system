<template>
  <router-view />
  <div class="toast-wrap">
    <transition-group name="toast">
      <div v-for="t in toasts" :key="t.id" :class="['toast', `toast-${t.type}`]">
        <span>{{ t.type === 'success' ? '✓' : t.type === 'error' ? '✕' : 'ℹ' }}</span>
        {{ t.message }}
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { reactive, provide } from 'vue'

const toasts = reactive([])
let tid = 0

function showToast(message, type = 'success') {
  const id = ++tid
  toasts.push({ id, message, type })
  setTimeout(() => {
    const i = toasts.findIndex(t => t.id === id)
    if (i !== -1) toasts.splice(i, 1)
  }, 3000)
}

provide('showToast', showToast)
</script>

<style>
.toast-enter-active, .toast-leave-active { transition: all 0.25s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(40px); }
</style>
