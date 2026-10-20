import { X } from '@/components/icons'
import { useUiStore } from '@/stores/uiStore'
import LoginCard from './LoginCard'
import RegisterCard from './RegisterCard'

/**
 * 登录/注册浮层。
 *
 * 挂在 App 顶层（RouterProvider 旁边）而不是某个布局里，这是刻意的：布局会随
 * 路由变化重挂，而浮层不该受影响 —— 用户在详情页被 axios 拦截器弹出登录卡、
 * 登录成功后路由跳转，卡片不能跟着闪一下。
 *
 * 这一层是**不吃事件**的居中容器，关键在 `pointer-events-none`：需求是"卡片显示在
 * 屏幕中间、背后的页面还能看还能滚"，所以浮层不拦点击与滚轮。事件只由卡片自己
 * （内层 `pointer-events-auto`）接收。
 *
 * 副作用是"点背景关闭"做不到 —— 背景根本不收点击。这是刻意的：让背景可点，就等于
 * 让用户在滚动页面时先被卡片吃掉一次点击。关闭统一走右上角的 X。
 *
 * **一层很淡的压暗是白底之后才加的**：以前那层是全透明的，因为卡片是深色的、
 * 压在黑色的底上本来就分得清。现在卡面和页面都是纯白，只靠描边和阴影区分，用户
 * 分不清哪张是"能输入的框"。`bg-foreground/25` 只压暗不挡事件 —— 上面那条
 * "背景还能看还能滚"仍然成立。嫌重就调这一个值。
 */
export default function AuthCardOverlay() {
  const visible = useUiStore((s) => s.authCardVisible)
  const mode = useUiStore((s) => s.authCardMode)
  const closeAuthCard = useUiStore((s) => s.closeAuthCard)

  if (!visible) return null

  return (
    <div className="pointer-events-none fixed inset-0 z-3000 flex items-center justify-center bg-foreground/25 p-6">
      <div className="pointer-events-auto relative flex max-h-[calc(100vh-3rem)]">
        <button
          type="button"
          title="关闭"
          aria-label="关闭"
          onClick={closeAuthCard}
          className="absolute top-3.5 right-3.5 z-10 flex size-7.5 cursor-pointer items-center justify-center rounded-full border border-border bg-card text-muted-foreground transition-transform duration-200 hover:rotate-90 hover:text-foreground"
        >
          <X size={16} />
        </button>

        {mode === 'login' ? <LoginCard /> : <RegisterCard />}
      </div>
    </div>
  )
}
