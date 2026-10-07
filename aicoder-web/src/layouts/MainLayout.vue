<script setup lang="ts">
import { onMounted } from 'vue'
import TopBar from './TopBar.vue'
import SideMenu from './SideMenu.vue'
import FooterBar from './FooterBar.vue'
import { useUserStore } from '@/stores/userStore'
import { useAppStore } from '@/stores/appStore'
import { useChatStore } from '@/stores/chatStore'
import { useMenuStore } from '@/stores/menuStore'

const userStore = useUserStore()
const appStore = useAppStore()
const chatStore = useChatStore()
const menuStore = useMenuStore()

onMounted(async () => {
  appStore.initTheme()
  try {
    await userStore.fetchUserInfo()
    await menuStore.fetchMenus()
    await chatStore.fetchModels()
  } catch {
    // 忽略
  }
})
</script>

<template>
  <div class="main-layout">
    <TopBar />
    <div class="main-body">
      <SideMenu />
      <main class="main-content">
        <router-view />
      </main>
    </div>
    <FooterBar />
  </div>
</template>

<style scoped lang="scss">
.main-layout {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.main-content {
  flex: 1;
  overflow: auto;
  background: var(--bg-root);
  padding: 24px;
  transition: background 0.4s ease;
}
</style>
