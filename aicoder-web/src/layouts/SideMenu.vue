<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/appStore'
import { useMenuStore } from '@/stores/menuStore'

const route = useRoute()
const appStore = useAppStore()
const menuStore = useMenuStore()
const isDragging = ref(false)
const expandedMenus = ref<Set<number>>(new Set())

const expandForRoute = () => {
  for (const menu of menuStore.menus) {
    if (menu.type === 1 && menu.children?.length) {
      if (menu.children.some(child => isActive(child.path))) {
        expandedMenus.value = new Set([...expandedMenus.value, menu.id])
      }
    }
  }
}

watch(() => route.path, () => expandForRoute())
watch(() => menuStore.loaded, (loaded) => { if (loaded) expandForRoute() })
onMounted(() => expandForRoute())

const iconMap: Record<string, string> = {
  Home: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6',
  Chat: 'M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z',
  Knowledge: 'M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253',
  Rag: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z',
  Workflow: 'M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z',
  Sql: 'M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4',
  VectorDb: 'M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4',
  Profile: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z',
  System: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z',
  User: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z',
  Role: 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z',
  Permission: 'M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z',
  Menu: 'M4 6h16M4 12h16M4 18h16',
  Skill: 'M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456z',
}

const getIconPath = (iconName: string): string => {
  return iconMap[iconName] || iconMap['Menu']
}

const isActive = (path: string) => {
  if (path === '/home') return route.path === '/home' || route.path === '/'
  return route.path === path || route.path.startsWith(path + '/')
}

const toggleExpand = (menuId: number) => {
  if (expandedMenus.value.has(menuId)) {
    expandedMenus.value.delete(menuId)
  } else {
    expandedMenus.value.add(menuId)
  }
}

const isCollapsed = computed(() => appStore.sideMenuCollapsed)
const menuWidth = computed(() => isCollapsed.value ? '52px' : `${appStore.sideMenuWidth}%`)

const startDrag = (e: MouseEvent) => {
  e.preventDefault()
  isDragging.value = true
  const startX = e.clientX
  const startWidth = appStore.sideMenuWidth
  const containerWidth = window.innerWidth

  const onMouseMove = (ev: MouseEvent) => {
    if (!isDragging.value) return
    const diff = ev.clientX - startX
    const percent = startWidth + (diff / containerWidth) * 100
    appStore.setSideMenuWidth(percent)
  }

  const onMouseUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}
</script>

<template>
  <aside class="side-menu" :class="{ collapsed: isCollapsed }" :style="{ width: menuWidth }">
    <!-- 菜单列表 -->
    <nav class="menu-list">
      <template v-for="menu in menuStore.menus" :key="menu.id">
        <!-- 目录菜单：有子菜单 -->
        <div v-if="menu.type === 1 && menu.children?.length" class="menu-group">
          <button
            class="menu-item menu-directory"
            :class="{ active: menu.children.some(child => isActive(child.path)) }"
            @click="toggleExpand(menu.id)"
            :title="isCollapsed ? menu.name : ''"
          >
            <span class="menu-indicator" />
            <svg class="menu-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path :d="getIconPath(menu.icon)" />
            </svg>
            <span v-if="!isCollapsed" class="menu-label">{{ menu.name }}</span>
            <svg
              v-if="!isCollapsed"
              class="expand-arrow"
              :class="{ expanded: expandedMenus.has(menu.id) }"
              width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
            >
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
          <div v-show="expandedMenus.has(menu.id) && !isCollapsed" class="menu-children">
            <router-link
              v-for="child in menu.children"
              :key="child.id"
              :to="child.path"
              class="menu-item menu-child"
              :class="{ active: isActive(child.path) }"
            >
              <span class="menu-indicator" />
              <svg class="menu-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                <path :d="getIconPath(child.icon)" />
              </svg>
              <span class="menu-label">{{ child.name }}</span>
            </router-link>
          </div>
        </div>
        <!-- 普通菜单项 -->
        <router-link
          v-else
          :to="menu.path"
          class="menu-item"
          :class="{ active: isActive(menu.path) }"
          :title="isCollapsed ? menu.name : ''"
        >
          <span class="menu-indicator" />
          <svg class="menu-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path :d="getIconPath(menu.icon)" />
          </svg>
          <span v-if="!isCollapsed" class="menu-label">{{ menu.name }}</span>
        </router-link>
      </template>
    </nav>

    <!-- 底部操作区 -->
    <div class="menu-footer">
      <button class="collapse-btn" @click="appStore.toggleSideMenu()">
        <svg v-if="!isCollapsed" width="16" height="16" viewBox="0 0 16 16">
          <path d="M11 2L4 8l7 6" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
          <line x1="13" y1="2" x2="13" y2="14" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" opacity="0.3"/>
        </svg>
        <svg v-else width="16" height="16" viewBox="0 0 16 16">
          <path d="M5 2l7 6-7 6" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>

    <!-- 拖拽手柄 -->
    <div
      v-if="!isCollapsed"
      class="resize-handle"
      :class="{ active: isDragging }"
      @mousedown="startDrag"
    />
  </aside>
</template>

<style scoped lang="scss">
.side-menu {
  position: relative;
  height: 100%;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--sidebar-border);
  display: flex;
  flex-direction: column;
  transition: width 0.28s cubic-bezier(0.33, 0, 0.15, 1), background 0.4s ease;
  overflow: hidden;
  flex-shrink: 0;
  user-select: none;
}

// --- 菜单列表 ---
.menu-list {
  flex: 1;
  padding: 8px 6px;
  overflow-y: auto;
  overflow-x: hidden;
}

.menu-group {
  margin-bottom: 1px;
}

.menu-children {
  padding-left: 12px;
}

.menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  margin-bottom: 1px;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--sidebar-text);
  transition: all 0.18s ease;
  white-space: nowrap;
  position: relative;
  text-align: left;
  text-decoration: none;
  font-size: inherit;
  border: none;
  cursor: pointer;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);

    .menu-indicator {
      opacity: 0.3;
      transform: scaleY(0.6);
    }
  }

  &.active {
    background: var(--sidebar-active-bg);
    color: var(--sidebar-active-text);
    font-weight: 500;

    .menu-indicator {
      opacity: 1;
      transform: scaleY(1);
      background: var(--accent);
    }

    .menu-icon {
      filter: drop-shadow(0 0 4px var(--accent-glow));
    }
  }
}

.menu-directory {
  .expand-arrow {
    margin-left: auto;
    transition: transform 0.25s cubic-bezier(0.33, 0, 0.15, 1);
    flex-shrink: 0;

    &.expanded {
      transform: rotate(90deg);
    }
  }
}

.menu-child {
  padding: 7px 10px;
  font-size: 12.5px;
}

// 左侧活跃指示竖条
.menu-indicator {
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 2.5px;
  border-radius: 2px;
  background: var(--accent);
  opacity: 0;
  transform: scaleY(0);
  transition: all 0.25s cubic-bezier(0.33, 0, 0.15, 1);
  transform-origin: center;
}

.menu-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  transition: filter 0.2s ease;
}

.menu-label {
  font-family: 'Sora', sans-serif;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.01em;
}

// 折叠状态
.collapsed .menu-item {
  justify-content: center;
  padding: 9px 6px;
}

.collapsed .menu-children {
  display: none;
}

// --- 底部操作 ---
.menu-footer {
  border-top: 1px solid var(--sidebar-border);
  padding: 4px;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 10px;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  transition: all 0.15s ease;

  svg {
    transition: transform 0.28s cubic-bezier(0.33, 0, 0.15, 1);
  }

  &:hover {
    background: var(--bg-hover);
    color: var(--text-secondary);
  }
}

// --- 拖拽手柄 ---
.resize-handle {
  position: absolute;
  top: 0;
  right: -2px;
  width: 5px;
  height: 100%;
  cursor: col-resize;
  z-index: 10;
  background: transparent;
  transition: background 0.15s, opacity 0.15s;

  &:hover,
  &.active {
    background: var(--accent);
    opacity: 0.35;
  }

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    left: -3px;
    right: 0;
  }
}
</style>
