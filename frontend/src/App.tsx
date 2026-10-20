import { RouterProvider } from 'react-router-dom'
import { Toaster } from '@/components/ui/sonner'
import { ConfirmHost } from '@/components/feedback/ConfirmHost'
import AuthCardOverlay from '@/components/auth/AuthCardOverlay'
// 显式带 .tsx：同目录下还留着 Vue 版 router/index.ts（P6 才删），
// 而 Vite 的默认解析顺序里 .ts 优先于 .tsx —— 写 '@/router' 会拿到那个。
import { router } from '@/router/index.tsx'

/**
 * 应用外壳。三样东西挂在 RouterProvider **之外**，理由各不相同但方向一致：
 * 它们都是全站的、只有一份的、且不该随路由重挂的。
 *
 *   - AuthCardOverlay：用户可能在任意页面被 axios 拦截器弹出登录卡（token 过期）。
 *     挂进布局的话，登录成功后路由一跳，布局重挂，卡片会闪一下。
 *   - Toaster：toast 是命令式的（`toast.error(...)` 在拦截器里调），
 *     宿主挂在哪都行，但要保证整个生命周期都在。
 *   - ConfirmHost：同上，`confirmDialog()` 由事件处理器调用，需要宿主常驻。
 */
export default function App() {
  return (
    <>
      <RouterProvider router={router} />
      <AuthCardOverlay />
      <Toaster position="top-center" />
      <ConfirmHost />
    </>
  )
}
