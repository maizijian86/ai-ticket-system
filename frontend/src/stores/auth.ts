import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserDTO, LoginResponse } from '@/types'
import { authApi } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const userInfo = ref<UserDTO | null>(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')
  const isHandler = computed(() => true) // All users can handle tickets

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    setAuthData(res)
    return res
  }

  async function register(data: { username: string; password: string; email: string; nickname?: string }) {
    return await authApi.register(data)
  }

  function setAuthData(data: LoginResponse) {
    userInfo.value = {
      id: data.userId,
      username: data.username,
      role: data.role as any,
      nickname: data.nickname,
      email: '',
      status: 'active'
    }

    localStorage.setItem('userId', String(data.userId))
    localStorage.setItem('userName', data.nickname || data.username)
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))

    setTokens(data.accessToken, data.refreshToken)
  }

  /**
   * 只更新双 Token。
   * 刷新流程走这里而不是直接写 localStorage，否则 Pinia 里的 token 会和存储漂移，
   * 而路由守卫读的正是 store 里的值。
   */
  function setTokens(accessToken: string, newRefreshToken?: string) {
    token.value = accessToken
    localStorage.setItem('token', accessToken)

    if (newRefreshToken) {
      refreshToken.value = newRefreshToken
      localStorage.setItem('refreshToken', newRefreshToken)
    }
  }

  /**
   * 只清本地登录态，不碰网络。
   * 拦截器里刷新失败时也走这个，所以这里绝不能发请求，否则会绕回拦截器形成递归。
   */
  function logout() {
    token.value = null
    refreshToken.value = null
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
    localStorage.removeItem('userName')
    localStorage.removeItem('userInfo')
  }

  /**
   * 用户主动登出：先尽力让后端撤销 refreshToken（否则它还能活 7 天），再清本地。
   * 网络失败不能挡住本地登出，不然用户会以为自己没退出去。
   */
  async function logoutRemote() {
    try {
      await authApi.logout()
    } catch {
      // 忽略：本地登出照常进行
    }
    logout()
  }

  return {
    token,
    refreshToken,
    userInfo,
    isLoggedIn,
    isAdmin,
    isHandler,
    login,
    register,
    setAuthData,
    setTokens,
    logout,
    logoutRemote
  }
})
