import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { User } from '@/types'
import { authApi } from '@/api/auth'
import { useMenuStore } from './menuStore'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const user = ref<User | null>(null)

  const isLoggedIn = () => !!token.value

  const login = async (username: string, password: string) => {
    const res = await authApi.login({ username, password })
    token.value = res.data.token
    localStorage.setItem('token', res.data.token)
    await fetchUserInfo()
    await useMenuStore().fetchMenus()
  }

  const register = async (data: { username: string; password: string; nickname: string; email: string }) => {
    await authApi.register(data)
  }

  const fetchUserInfo = async () => {
    const res = await authApi.getUserInfo()
    user.value = res.data
  }

  const logout = () => {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    useMenuStore().clearMenus()
  }

  return { token, user, isLoggedIn, login, register, fetchUserInfo, logout }
})
