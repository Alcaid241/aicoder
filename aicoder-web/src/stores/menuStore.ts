import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MenuTreeNode } from '@/types'
import { systemApi } from '@/api/system'

export const useMenuStore = defineStore('menu', () => {
  const menus = ref<MenuTreeNode[]>([])
  const loaded = ref(false)

  const fetchMenus = async () => {
    try {
      const res = await systemApi.menuUserTree()
      menus.value = res.data
      loaded.value = true
    } catch {
      menus.value = []
      loaded.value = false
    }
  }

  const clearMenus = () => {
    menus.value = []
    loaded.value = false
  }

  return { menus, loaded, fetchMenus, clearMenus }
})
