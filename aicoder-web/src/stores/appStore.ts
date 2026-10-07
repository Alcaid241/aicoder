import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ThemeMode } from '@/types'

export const useAppStore = defineStore('app', () => {
  const theme = ref<ThemeMode>((localStorage.getItem('theme') as ThemeMode) || 'obsidian')
  const sideMenuCollapsed = ref(false)
  const sideMenuWidth = ref(16)

  const setTheme = (mode: ThemeMode) => {
    theme.value = mode
    localStorage.setItem('theme', mode)
    document.documentElement.setAttribute('data-theme', mode)
  }

  const toggleSideMenu = () => {
    sideMenuCollapsed.value = !sideMenuCollapsed.value
  }

  const setSideMenuWidth = (width: number) => {
    sideMenuWidth.value = Math.min(40, Math.max(15, width))
  }

  const initTheme = () => {
    document.documentElement.setAttribute('data-theme', theme.value)
  }

  return { theme, sideMenuCollapsed, sideMenuWidth, setTheme, toggleSideMenu, setSideMenuWidth, initTheme }
})
