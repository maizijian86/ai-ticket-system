import { createBrowserRouter, redirect } from 'react-router-dom'
import type { ComponentType } from 'react'
import AppLayout from '@/layouts/AppLayout'
import Placeholder from '@/components/feedback/Placeholder'
import { requireAdminLoader, requireAuthLoader } from './guards'

/**
 * 路由表。
 *
 * ── 为什么页面全部改成懒加载（Phase 6）──
 * 之前所有页面都是静态 import，`vite build` 产出**单个 950 kB 的 chunk**
 * （gzip 318 kB）—— 首屏为了显示一张落地页，要把管理后台、统计图表、
 * AI 工作台、工单详情的代码全下下来。
 *
 * 现在每个页面各自成 chunk，首页只拉它需要的部分。注意 `AppLayout` 仍是静态
 * import：它是外壳，任何路由都要用，拆出去只会多一次往返。
 *
 * ── 写法 ──
 * react-router v7 的 `lazy` 接受一个返回模块的函数，模块里用 `Component` 这个
 * 键指定默认导出。这里封了一个 `page()` 免得 11 条路由各抄一遍：
 *   `page(() => import('@/pages/...'))`
 *
 * ── 路径与守卫语义没有变 ──
 * 包括那些看着像历史遗留的重定向 —— 它们是真实存在的 URL：在书签、聊天记录、
 * 老侧边栏里，掉进 404 比回到大厅不友善得多。
 */

/**
 * 懒加载一个页面组件。
 *
 * `import()` 的结果是 `{ default: Component }`，而 router 要的是 `{ Component }`，
 * 键名不一样，所以要转一次 —— 直接写 `lazy: () => import(...)` 会得到一个
 * 永远匹配不到组件的路由（表现为整页空白，控制台没有任何报错）。
 */
function page(loader: () => Promise<{ default: ComponentType }>) {
  return async () => {
    const mod = await loader()
    return { Component: mod.default }
  }
}

/** 还没做的模块，渲染"即将上线"。只有项目管理在用（见 Placeholder 的注释）。 */
const comingSoon = (title: string) => {
  const El = () => <Placeholder title={title} />
  return { Component: El }
}

const toTickets = () => redirect('/tickets')

export const router = createBrowserRouter([
  // 此处原有 /login 与 /register 两条整页路由。登录/注册改成了浮层卡片，
  // 两个页面已删除；URL 保留为重定向。
  { path: '/login', loader: toTickets },
  { path: '/register', loader: toTickets },

  {
    path: '/',
    Component: AppLayout,
    children: [
      // 首页。对访客开放、**没有守卫** —— 这是整个应用的门口，门口不该弹登录卡。
      { index: true, lazy: page(() => import('@/pages/Home')) },

      // 工单管理。**这两条对访客开放**（没有 loader）：
      // 详情页的 AI 内容之外全部可见，AI 区域在页面内遮罩。访客是这个漏斗里
      // 最该被留住的人，守卫把他弹走就什么也留不下了。
      { path: 'tickets', lazy: page(() => import('@/pages/ticket/TicketList')) },
      { path: 'tickets/:id', lazy: page(() => import('@/pages/ticket/TicketDetail')) },

      // 下面这些默认需要登录（守卫语义是"不写 loader 就要登录"）。
      { path: 'tickets/new', loader: requireAuthLoader, lazy: page(() => import('@/pages/ticket/TicketCreate')) },
      { path: 'tickets/:id/edit', loader: requireAuthLoader, lazy: page(() => import('@/pages/ticket/TicketEdit')) },
      { path: 'tickets/:id/chat', loader: requireAuthLoader, lazy: page(() => import('@/pages/ticket/TicketChat')) },

      // 此处原有 /my-tickets、/pending、/accepted、/completed 四条路由和对应页面。
      // 那四页说的是和个人流水一样的事，只是换了个位置，已并进需求大厅顶上的卡片，
      // 页面删除；URL 同样保留为重定向。
      { path: 'my-tickets', loader: toTickets },
      { path: 'pending', loader: toTickets },
      { path: 'accepted', loader: toTickets },
      { path: 'completed', loader: toTickets },

      // 「知识库」入口与页面都已撤掉（2026-10）：后端 knowledge_base 表没有任何代码
      // 读写 —— 它唯一的消费者是已删除的 ai-service 里那套旧 AiService。
      // URL 保留为重定向，理由同上：书签里可能还有它。
      { path: 'knowledge', loader: toTickets },

      // 项目管理。后端 35 个接口可用，前端三页属于单独排期的工作量，
      // 本轮先用「即将上线」承接（见 components/feedback/Placeholder.tsx）。
      { path: 'projects', loader: requireAuthLoader, ...comingSoon('项目管理') },
      // 必须排在 projects/:id 之前 —— 静态段优先，但把这条写在前面能让
      // 「create 被当成 id」这件事一眼可见。
      { path: 'projects/create', loader: requireAuthLoader, ...comingSoon('新建项目') },
      { path: 'projects/:id', loader: requireAuthLoader, ...comingSoon('项目详情') },

      // 会员与个人主页。三条都要登录：会员页要显示自己的余额与到期日、结果页要查
      // 自己那笔单（后端按网关注入的身份校验归属，未登录会 401），个人主页更不用说。
      { path: 'membership', loader: requireAuthLoader, lazy: page(() => import('@/pages/membership/Membership')) },
      // 支付宝的 return_url 也指向这里（application.yml 的 alipay.return-url），
      // 单号由支付宝以 out_trade_no 追加在 query 上。
      { path: 'membership/result', loader: requireAuthLoader, lazy: page(() => import('@/pages/membership/MembershipResult')) },
      { path: 'profile', loader: requireAuthLoader, lazy: page(() => import('@/pages/profile/Profile')) },

      { path: 'admin/users', loader: requireAdminLoader, lazy: page(() => import('@/pages/admin/AdminUsers')) },
      { path: 'admin/stats', loader: requireAdminLoader, lazy: page(() => import('@/pages/admin/AdminStats')) },

      // 兜底：必须放在所有子路由之后。没有它时，任何未匹配的路径都会渲染成
      // 布局 + 空的 Outlet，表现为整页空白，既不报错也没有任何提示。
      // 放在子路由里是为了保留顶栏，用户能直接点走而不是卡在死页。
      { path: '*', lazy: page(() => import('@/pages/NotFound')) }
    ]
  }
])
