import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserDTO, LoginResponse, RegisterRequest } from '@/types'
import { authApi } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const userInfo = ref<UserDTO | null>(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')

  // ---------------------------------------------------------------- 登录卡片
  // 登录/注册从"整页路由"改成了"屏幕中间的浮层卡片"，所以它不再是路由状态，
  // 而是全局 UI 状态。放在 store 里而不是某个组件的 ref：需要开它的地方散落在大厅、
  // 工单详情、顶栏、axios 拦截器（token 过期时）—— 它们拿不到 AppLayout 的局部状态。
  //
  // **刻意不持久化**：刷新页面时卡片应该消失。用 localStorage 存会让用户下次打开
  // 网站时被一张"上次没关掉的登录框"挡住，那比不弹更烦人。
  const authCardVisible = ref(false)
  const authCardMode = ref<'login' | 'register'>('login')

  function openAuthCard(mode: 'login' | 'register' = 'login') {
    authCardMode.value = mode
    authCardVisible.value = true
  }

  function closeAuthCard() {
    authCardVisible.value = false
  }

  function switchAuthCard(mode: 'login' | 'register') {
    authCardMode.value = mode
  }
  // 此处原有 isHandler = computed(() => true)：一句恒为 true 的死代码（"所有用户
  // 都能接单"是 2026-04-18 那次合并的遗留），没有任何消费者。身份判断现在一律
  // 按 userInfo.role 直读，见 views/ticket/TicketList.vue。

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    setAuthData(res)
    // 登录成功就收起卡片。放在这里而不是登录卡片组件里：卡片有多个入口
    // （大厅横幅、详情页遮罩、顶栏、token 过期拦截器），漏掉任何一处的关闭，
    // 用户都会在已经登录之后还被一张登录框挡着。
    closeAuthCard()
    return res
  }

  async function register(data: RegisterRequest) {
    return await authApi.register(data)
  }

  function setAuthData(data: LoginResponse) {
    userInfo.value = {
      id: data.userId,
      username: data.username,
      // 此处原有 `as any`：Role 联合类型缺 HANDLER/PUBLISHER 时靠它压掉了报错，
      // 于是后端加了身份、前端类型没跟上，编译器一声不吭。union 已补全，去掉断言。
      role: data.role,
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
    authCardVisible,
    authCardMode,
    openAuthCard,
    closeAuthCard,
    switchAuthCard,
    login,
    register,
    setAuthData,
    setTokens,
    logout,
    logoutRemote
  }
})
