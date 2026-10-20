import { create } from 'zustand'
import { toast } from 'sonner'
import type { UserDTO, LoginResponse, RegisterRequest } from '@/types'
import { authApi } from '@/api'
import { useUiStore } from './uiStore'

/**
 * 登录态。这是 `stores/auth.ts`（Vue/Pinia 版）的 React 对应物，逐条对齐语义。
 *
 * 为什么用 Zustand 而不是 Context：**axios 拦截器和路由 loader 都要在组件树外
 * 读写它**。Context 必须挂在某个 Provider 下、只能在组件内 use，而 forceLogin()
 * 是模块级函数、loader 在渲染前跑 —— 两者都拿不到 Context。Zustand 的 store 是
 * 模块级单例，`useAuthStore.getState()` 在哪儿都能调，正好等价于 Pinia 在
 * 守卫里 `useAuthStore()` 的用法。
 *
 * 状态存在 localStorage（刷新后仍登录），但**内存态才是唯一真相**：所有写入都
 * 同时落两处，读取一律走 store —— 否则会出现 store 和存储漂移，而路由守卫读的
 * 正是 store 里的值。
 */
interface AuthState {
  token: string | null
  refreshToken: string | null
  userInfo: UserDTO | null

  login: (username: string, password: string) => Promise<LoginResponse>
  register: (data: RegisterRequest) => Promise<UserDTO>
  setAuthData: (data: LoginResponse) => void
  /**
   * 只替换 userInfo（昵称、头像这类"资料"改动）。
   *
   * 为什么不能复用 `setAuthData`：那个入参是**登录响应**，要求带 accessToken /
   * refreshToken，改个头像就得把 token 从 localStorage 读出来再塞回去 ——
   * 又假又容易写错。这里单独开一条只动资料的路。
   *
   * 必须同时写 localStorage：boot 时 userInfo 是从那里恢复的，只改内存态的话
   * 刷新一次就退回旧昵称（顶栏头像也是读 store，会跟着一起漂）。
   */
  setUserInfo: (userInfo: UserDTO) => void
  setTokens: (accessToken: string, newRefreshToken?: string) => void
  logout: () => void
  logoutRemote: () => Promise<void>
}

/** 登录态派生值。写成 selector 而不是 state 字段，免得多一个会和 token 漂移的副本。 */
export const selectIsLoggedIn = (s: AuthState) => !!s.token
export const selectIsAdmin = (s: AuthState) => s.userInfo?.role === 'ADMIN'

/** 读取当前用户的展示名，顶栏等处用。 */
export const selectDisplayName = (s: AuthState) =>
  s.userInfo?.nickname || s.userInfo?.username || ''

export const useAuthStore = create<AuthState>()((set, get) => ({
  token: localStorage.getItem('token'),
  refreshToken: localStorage.getItem('refreshToken'),
  userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null'),

  async login(username, password) {
    const res = await authApi.login({ username, password })
    get().setAuthData(res)
    // 登录成功就收起卡片。放在这里而不是登录卡片组件里：卡片有多个入口
    //（大厅横幅、详情页遮罩、顶栏、token 过期拦截器），漏掉任何一处的关闭，
    // 用户都会在已经登录之后还被一张登录框挡着。
    useUiStore.getState().closeAuthCard()
    // 登录成功的提示放在 store 而不是卡片里：卡片关闭也是在这里做的，两个反馈
    // 成对出现，将来加新的登录入口也不会漏提示。
    const name = res.nickname || res.username
    toast.success(name ? `登录成功，欢迎回来 ${name}` : '登录成功')
    return res
  },

  register(data) {
    return authApi.register(data)
  },

  setAuthData(data) {
    const userInfo: UserDTO = {
      id: data.userId,
      username: data.username,
      role: data.role,
      nickname: data.nickname,
      // 登录响应里必须带头像：登录后不回头调 /users/{id}，这里丢了就要等刷新才补上
      avatarUrl: data.avatarUrl,
      email: '',
      status: 'active'
    }
    set({ userInfo })

    localStorage.setItem('userId', String(data.userId))
    localStorage.setItem('userName', data.nickname || data.username)
    localStorage.setItem('userInfo', JSON.stringify(userInfo))

    get().setTokens(data.accessToken, data.refreshToken)
  },

  setUserInfo(userInfo) {
    set({ userInfo })
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
    localStorage.setItem('userName', userInfo.nickname || userInfo.username)
  },

  /**
   * 只更新双 Token。
   * 刷新流程走这里而不是直接写 localStorage，否则 store 里的 token 会和存储漂移，
   * 而路由守卫读的正是 store 里的值。
   */
  setTokens(accessToken, newRefreshToken) {
    localStorage.setItem('token', accessToken)
    if (newRefreshToken) {
      localStorage.setItem('refreshToken', newRefreshToken)
    }
    set((s) => ({
      token: accessToken,
      refreshToken: newRefreshToken ?? s.refreshToken
    }))
  },

  /**
   * 只清本地登录态，不碰网络。
   * 拦截器里刷新失败时也走这个，所以这里绝不能发请求，否则会绕回拦截器形成递归。
   */
  logout() {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
    localStorage.removeItem('userName')
    localStorage.removeItem('userInfo')
    set({ token: null, refreshToken: null, userInfo: null })
  },

  /**
   * 用户主动登出：先尽力让后端撤销 refreshToken（否则它还能活 7 天），再清本地。
   * 网络失败不能挡住本地登出，不然用户会以为自己没退出去。
   */
  async logoutRemote() {
    try {
      await authApi.logout()
    } catch {
      // 忽略：本地登出照常进行
    }
    get().logout()
    // 只在这一条路径提示：`logout()` 自己也被拦截器用来处理"刷新失败/登录过期"，
    // 那种情况已经有 401 的错误 toast，再补一句"已退出登录"像是用户自己点的。
    toast.success('已退出登录')
  }
}))
