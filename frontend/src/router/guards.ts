import { redirect } from 'react-router-dom'
import { selectIsAdmin, useAuthStore } from '@/stores/authStore'
import { useUiStore } from '@/stores/uiStore'

/**
 * 路由守卫。用 React Router 的 **loader** 而不是包装组件。
 *
 * 为什么不是包装组件（`<RequireAuth><Profile/></RequireAuth>`）：包装组件里的检查
 * 跑在**渲染期**，也就是受保护的页面已经渲染了一帧、它的 useEffect 已经发出请求，
 * 才发现"哦没登录"再把用户弹走 —— 表现为受保护内容闪一下。loader 在渲染前跑完，
 * 不通过就直接换路由，页面根本不会挂载。
 *
 * 为什么守卫能在这里读 store：Zustand 的 store 是模块级单例，
 * `useAuthStore.getState()` 在组件树外（包括非 React 的路由层）随时可调。
 * 这正好等价于旧版 Pinia 在 vue-router 守卫里 `useAuthStore()` 的用法。
 *
 * 守卫语义是**默认需登录**：路由不写 loader 就是公开页。目前显式公开的只有
 * `tickets` 和 `tickets/:id` 两条。
 */

/**
 * 需要登录。不通过时**先把登录卡片摆出来，再跳大厅**。
 *
 * 顺序很重要，不是随便哪个先：这套动作要传达的是"你被拦下来了，原因是没登录，
 * 解决方式是这里"。旧版曾经是 `next('/login')` 直接跳走、什么都不说 —— 用户点了
 * 个按钮页面无理由地跳走，他只会以为坏了。
 *
 * 代价是丢失了原目标路径（比如从 /tickets/new 被弹回来，登录后停在 /tickets）。
 * 要补这个得把 redirect 存进 store 或 query，属于体验优化，迁移期间不夹带。
 */
export function requireAuthLoader() {
  if (!useAuthStore.getState().token) {
    useUiStore.getState().openAuthCard('login')
    return redirect('/tickets')
  }
  return null
}

/**
 * 需要管理员。**不足时静默跳走，不弹卡片** —— 用户已经登录了，弹登录卡没有意义，
 * 只会让他以为自己掉线了。
 *
 * 没登录的情况仍走和 requireAuthLoader 一样的分支：先弹卡再跳。
 *（旧版是靠 `requiresAuth !== false && !token` 那一关先拦下来保证的。）
 */
export function requireAdminLoader() {
  const state = useAuthStore.getState()
  if (!state.token) {
    useUiStore.getState().openAuthCard('login')
    return redirect('/tickets')
  }
  if (!selectIsAdmin(state)) {
    return redirect('/tickets')
  }
  return null
}
