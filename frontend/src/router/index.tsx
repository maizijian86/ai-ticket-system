import { createBrowserRouter, redirect } from 'react-router-dom'
import AppLayout from '@/layouts/AppLayout'
import Home from '@/pages/Home'
import TicketDetail from '@/pages/ticket/TicketDetail'
import TicketList from '@/pages/ticket/TicketList'
import TicketCreate from '@/pages/ticket/TicketCreate'
import TicketEdit from '@/pages/ticket/TicketEdit'
import TicketChat from '@/pages/ticket/TicketChat'
import Membership from '@/pages/membership/Membership'
import MembershipResult from '@/pages/membership/MembershipResult'
import Profile from '@/pages/profile/Profile'
import AdminUsers from '@/pages/admin/AdminUsers'
import AdminStats from '@/pages/admin/AdminStats'
import NotFound from '@/pages/NotFound'
import Placeholder from '@/components/feedback/Placeholder'
import { requireAdminLoader, requireAuthLoader } from './guards'

/**
 * 路由表。逐条对齐旧的 `router/index.ts`（vue-router），差别只在表达方式。
 *
 * 路径和守卫语义**一模一样**，包括那些看着像历史遗留的重定向 —— 它们是真实存在
 * 的 URL：在书签、聊天记录、老侧边栏里，掉进 NotFound 比回到大厅不友善得多。
 */

/** 未迁移的页面统一渲染占位。P3–P5 逐个换成真页面。 */
const page = (title: string) => <Placeholder title={title} />

const toTickets = () => redirect('/tickets')

export const router = createBrowserRouter([
  // 此处原有 /login 与 /register 两条整页路由（views/auth/Login.vue、Register.vue）。
  // 登录/注册改成了浮层卡片，两个页面已删除；URL 保留为重定向。
  { path: '/login', loader: toTickets },
  { path: '/register', loader: toTickets },

  {
    path: '/',
    Component: AppLayout,
    children: [
      // 首页。此前这里是个到 /tickets 的重定向，于是"打开项目"这件事
      // 就等于"打开一张工单列表"。现在它是真的一页：一句开场 + 两个按钮
      // （进大厅 / 登录），卷轴文字也只在它这里出现。
      //
      // 对访客开放，且**没有守卫** —— 这是整个应用的门口，门口不该弹登录卡。
      { index: true, element: <Home /> },

      // 工单管理。**这两条对访客开放**（没有 loader）：
      // 详情页的 AI 内容之外全部可见，AI 区域在页面内遮罩。访客是这个漏斗里
      // 最该被留住的人，守卫把他弹走就什么也留不下了。
      { path: 'tickets', element: <TicketList /> },
      { path: 'tickets/:id', element: <TicketDetail /> },

      // 下面这些默认需要登录（守卫语义是"不写 loader 就要登录"）。
      { path: 'tickets/new', loader: requireAuthLoader, element: <TicketCreate /> },
      { path: 'tickets/:id/edit', loader: requireAuthLoader, element: <TicketEdit /> },
      { path: 'tickets/:id/chat', loader: requireAuthLoader, element: <TicketChat /> },

      // 此处原有 /my-tickets、/pending、/accepted、/completed 四条路由和对应页面。
      // 那四页说的是和个人流水一样的事，只是换了个位置，已并进需求大厅顶上的卡片，
      // 页面删除；URL 同样保留为重定向。
      { path: 'my-tickets', loader: toTickets },
      { path: 'pending', loader: toTickets },
      { path: 'accepted', loader: toTickets },
      { path: 'completed', loader: toTickets },

      // 「知识库」入口与页面都已撤掉（2026-10）：后端 knowledge_base 表没有任何代码
      // 读写 —— 它唯一的消费者是已删除的 ai-service 里那套旧 AiService。
      // URL 保留为重定向，与上面那四条同样的理由：书签/聊天记录里可能还有它。
      { path: 'knowledge', loader: toTickets },

      // 项目管理。后端 35 个接口可用，前端三页属于单独排期的工作量，
      // 本轮先用「即将上线」承接（见 components/feedback/Placeholder.tsx）。
      { path: 'projects', loader: requireAuthLoader, element: page('项目管理') },
      // 必须排在 projects/:id 之前 —— 静态段优先，但把这条写在前面能让
      // 「create 被当成 id」这件事一眼可见。
      { path: 'projects/create', loader: requireAuthLoader, element: page('新建项目') },
      { path: 'projects/:id', loader: requireAuthLoader, element: page('项目详情') },

      // 会员与个人主页。三条都要登录：会员页要显示自己的余额与到期日、结果页要查
      // 自己那笔单（后端按网关注入的身份校验归属，未登录会 401），个人主页更不用说。
      { path: 'membership', loader: requireAuthLoader, element: <Membership /> },
      // 支付宝的 return_url 也指向这里（application.yml 的 alipay.return-url），
      // 单号由支付宝以 out_trade_no 追加在 query 上。
      { path: 'membership/result', loader: requireAuthLoader, element: <MembershipResult /> },
      { path: 'profile', loader: requireAuthLoader, element: <Profile /> },

      { path: 'admin/users', loader: requireAdminLoader, element: <AdminUsers /> },
      { path: 'admin/stats', loader: requireAdminLoader, element: <AdminStats /> },

      // 兜底：必须放在所有子路由之后。没有它时，任何未匹配的路径都会渲染成
      // 布局 + 空的 Outlet，表现为整页空白，既不报错也没有任何提示。
      // 放在子路由里是为了保留顶栏，用户能直接点走而不是卡在死页。
      { path: '*', element: <NotFound /> }
    ]
  }
])
