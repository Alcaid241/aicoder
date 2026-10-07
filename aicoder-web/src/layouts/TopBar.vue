<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'
import { useAppStore } from '@/stores/appStore'
import type { ThemeMode } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const showUserMenu = ref(false)
const userAreaRef = ref<HTMLElement | null>(null)

const themes: { mode: ThemeMode; label: string; icon: string }[] = [
  { mode: 'obsidian', label: '黑曜石', icon: '○' },
  { mode: 'parchment', label: '羊皮纸', icon: '◐' },
  { mode: 'midnight', label: '午夜蓝', icon: '●' }
]

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

const toggleUserMenu = () => { showUserMenu.value = !showUserMenu.value }

const handleClickOutside = (e: MouseEvent) => {
  if (userAreaRef.value && !userAreaRef.value.contains(e.target as Node)) {
    showUserMenu.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
</script>

<template>
  <header class="top-bar">
    <div class="brand">
      <svg class="logo-mark" width="22" height="22" viewBox="0 0 24 24" fill="none">
        <path d="M12 2l6 4v6l-6 4-6-4V6l6-4z" stroke="currentColor" stroke-width="1.8" fill="none" stroke-linejoin="round"/>
        <circle cx="12" cy="12" r="2" fill="currentColor"/>
      </svg>
      <span class="brand-name">AI Coder</span>
    </div>

    <div class="actions">
      <div class="theme-dots">
        <button
          v-for="t in themes" :key="t.mode"
          class="theme-dot"
          :class="{ active: appStore.theme === t.mode }"
          :title="t.label"
          @click="appStore.setTheme(t.mode)"
        >{{ t.icon }}</button>
      </div>

      <div class="user-area" ref="userAreaRef">
        <button class="user-btn" @click="toggleUserMenu">
          <span class="avatar">{{ userStore.user?.nickname?.charAt(0) || 'U' }}</span>
          <span class="name">{{ userStore.user?.nickname || '用户' }}</span>
          <svg width="10" height="10" viewBox="0 0 10 10"><path d="M2 3.5l3 3 3-3" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round"/></svg>
        </button>
        <div v-if="showUserMenu" class="dropdown">
          <button @click="router.push('/profile'); showUserMenu = false">
            <svg width="14" height="14" viewBox="0 0 14 14"><circle cx="7" cy="4" r="2.5" stroke="currentColor" stroke-width="1.3" fill="none"/><path d="M2 13c0-2.8 2.2-5 5-5s5 2.2 5 5" stroke="currentColor" stroke-width="1.3" fill="none"/></svg>
            个人信息
          </button>
          <div class="divider" />
          <button class="danger" @click="handleLogout">
            <svg width="14" height="14" viewBox="0 0 14 14"><path d="M5 2H2v10h3M5 7h6M8 4l3 3-3 3" stroke="currentColor" stroke-width="1.3" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
            退出登录
          </button>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
.top-bar {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--topbar-bg);
  border-bottom: 1px solid var(--topbar-border);
  flex-shrink: 0;
  z-index: 100;
  backdrop-filter: blur(12px);
  transition: background 0.4s ease, border-color 0.3s ease;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-mark {
  color: var(--accent);
}

.brand-name {
  font-family: 'Sora', 'PingFang SC', sans-serif;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.theme-dots {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: var(--radius-full);
  background: var(--bg-overlay);
}

.theme-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-muted);
  transition: all 0.2s ease;

  &:hover { color: var(--text-primary); }
  &.active {
    background: var(--bg-surface);
    color: var(--accent);
    box-shadow: var(--shadow-xs);
  }
}

.user-area { position: relative; }

.user-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px 5px 5px;
  border-radius: var(--radius-full);
  color: var(--text-primary);
  transition: background 0.2s;
  &:hover { background: var(--bg-hover); }
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--accent);
  color: var(--text-inverse);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 12px;
  font-family: 'Sora', sans-serif;
}

.name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  min-width: 160px;
  overflow: hidden;
  z-index: 200;
  animation: slideDown 0.15s ease;

  button {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    color: var(--text-primary);
    font-size: 13px;
    transition: background 0.12s;
    &:hover { background: var(--bg-hover); }
    &.danger { color: var(--danger); }
  }

  .divider {
    height: 1px;
    background: var(--border-subtle);
  }
}
</style>
