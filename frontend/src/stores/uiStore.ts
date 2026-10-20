import { create } from 'zustand'

/**
 * 登录/注册浮层的显隐。
 *
 * 旧版这份状态混在 auth store 里（Vue 的 Pinia）。拆出来是刻意的：
 * authStore 答的是「我是谁」，uiStore 答的是「屏幕上此刻开着什么」。
 * 前者被 axios 拦截器、路由 loader、顶栏读；后者被布局和 12 个调用点写 ——
 * 混在一起会让「清空登录态」和「关掉浮层」这两件事永远纠缠不清（比如登出时
 * 到底该不该顺手把卡片关上，两种语义都能自圆其说）。
 *
 * **刻意不持久化**：刷新页面时卡片应该消失。存 localStorage 会让用户下次打开
 * 网站时被一张"上次没关掉的登录框"挡住，比不弹更烦人。
 */
interface UiState {
  authCardVisible: boolean
  authCardMode: 'login' | 'register'

  openAuthCard: (mode?: 'login' | 'register') => void
  closeAuthCard: () => void
  switchAuthCard: (mode: 'login' | 'register') => void
}

export const useUiStore = create<UiState>()((set) => ({
  authCardVisible: false,
  authCardMode: 'login',

  openAuthCard: (mode = 'login') => set({ authCardMode: mode, authCardVisible: true }),
  closeAuthCard: () => set({ authCardVisible: false }),
  switchAuthCard: (mode) => set({ authCardMode: mode })
}))
