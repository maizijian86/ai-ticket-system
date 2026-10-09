import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  // 此处原有 /login 与 /register 两条整页路由（views/auth/Login.vue、
  // Register.vue）。登录/注册改成了屏幕中间的浮层卡片（components/auth/），
  // 两个页面已删除。
  //
  // 这两个 URL 保留为**重定向**而不是直接抹掉：它们在聊天记录、收藏夹里到处都是，
  // 掉进 NotFound 比回到大厅不友善。重定向的目标是 /tickets —— 大厅是公开页，
  // 未登录也能正常渲染，用户在那里能直接找到"登录"入口。
  { path: '/login', redirect: '/tickets' },
  { path: '/register', redirect: '/tickets' },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/tickets',
    children: [
      // 工单管理（保留）
      {
        path: 'tickets',
        name: 'TicketList',
        component: () => import('@/views/ticket/TicketList.vue'),
        meta: { requiresAuth: false }
      },
      {
        path: 'tickets/new',
        name: 'TicketCreate',
        component: () => import('@/views/ticket/TicketCreate.vue')
      },
      {
        // 详情页对访客开放：AI 内容之外的字段全部可见，AI 区域在页面内遮罩。
        // 落地这条 meta 之后，访客点开一张工单不会再被守卫弹走 —— 他是这个
        // 漏斗里最该被留住的人，弹走就什么也留不下了。
        path: 'tickets/:id',
        name: 'TicketDetail',
        component: () => import('@/views/ticket/TicketDetail.vue'),
        meta: { requiresAuth: false }
      },
      {
        path: 'tickets/:id/edit',
        name: 'TicketEdit',
        component: () => import('@/views/ticket/TicketEdit.vue')
      },
      {
        path: 'tickets/:id/chat',
        name: 'TicketChat',
        component: () => import('@/views/ticket/TicketChat.vue')
      },
      // 此处原有 /my-tickets、/pending、/accepted、/completed 四条路由和它们对应的
      // 四个页面（views/ticket/{MyTickets,PendingTickets,AcceptedTickets,
      // CompletedTickets}.vue）。那四页说的是和个人流水一样的事，只是换了个位置，
      // 已并进需求大厅顶上的卡片（按身份分叉，见 views/ticket/TicketList.vue），
      // 页面一并删除。
      //
      // 下面四条是**重定向**而不是直接抹掉：这几个 URL 在侧边栏挂过很久，
      // 用户书签、聊天里贴过的链接都会指向它们，留着 redirect 比掉进 NotFound
      // 友善得多。
      { path: 'my-tickets', redirect: '/tickets' },
      { path: 'pending', redirect: '/tickets' },
      { path: 'accepted', redirect: '/tickets' },
      { path: 'completed', redirect: '/tickets' },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/Knowledge.vue')
      },
      // 项目管理
      // 此前整块 views/project/ 都没有注册路由，页面无入口、点进去是空白。
      {
        path: 'projects',
        name: 'ProjectList',
        component: () => import('@/views/project/ProjectList.vue')
      },
      // 必须排在 projects/:id 之前：vue-router 的静态段优先于动态段，
      // 但把这条写在前面能让「create 被当成 id」这件事一眼可见。
      {
        path: 'projects/create',
        name: 'ProjectCreate',
        component: () => import('@/views/project/ProjectCreate.vue')
      },
      {
        path: 'projects/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/ProjectDetail.vue')
      },
      // 会员与个人主页。
      //
      // 三条**都不写** meta.requiresAuth:false，也就是默认需要登录 —— 会员页要显示
      // 自己的余额与到期日、结果页要查自己那笔单（后端按网关注入的身份校验归属，
      // 未登录会 401），个人主页更不用说。让守卫拦下来顺便弹出登录卡片，
      // 比放进去再各自处理 401 更省事。
      //
      // 代价：用户在结果页时 token 失效会被弹回大厅，丢掉那个 out_trade_no。
      // 但订单本身在库里，个人主页的「我的订单」能再点回来，所以不补 redirect。
      {
        path: 'membership',
        name: 'Membership',
        component: () => import('@/views/membership/Membership.vue')
      },
      {
        // 支付宝的 return_url 也指向这里（见 application.yml 的 alipay.return-url：
        // http://localhost:3000/membership/result），单号由支付宝以 out_trade_no
        // 追加在 query 上；页面对两种参数名都认。
        path: 'membership/result',
        name: 'MembershipResult',
        component: () => import('@/views/membership/MembershipResult.vue')
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/Profile.vue')
      },
      {
        path: 'admin/users',
        name: 'UserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'admin/stats',
        name: 'Stats',
        component: () => import('@/views/admin/Stats.vue'),
        meta: { requiresAdmin: true }
      },
      // 兜底：必须放在所有子路由之后。
      // 没有它时，任何未匹配的路径都会渲染成布局 + 空的 <router-view>，
      // 表现为整页空白，既不报错也没有任何提示。
      // 放在 AppLayout 内部是为了保留侧边栏，用户能直接点走而不是卡在死页。
      {
        path: ':pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/NotFound.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth !== false && !authStore.token) {
    // 需要登录的页面 → 回大厅 + **把登录卡片摆出来**。
    //
    // 这里不再 next('/login')：整页登录路由已删。更重要的是，"拦下来什么都不说"
    // 是原来最大的问题 —— 用户点了个按钮，页面无理由地跳走，他只会以为坏了。
    // 开卡片等于把"为什么被拦 + 怎么办"直接说清楚。
    //
    // 代价是丢失了原目标路径（比如从 /tickets/new 被弹回来，登录后停在 /tickets
    // 而不是新建页）。要补这个需要把 redirect 存进 store 或 query，属于体验优化，
    // 这次不做 —— 保持简单，且不会留下"登录后跳到一个权限已经变化的旧页面"的坑。
    next('/tickets')
    authStore.openAuthCard('login')
  } else if (to.meta.requiresAdmin && authStore.userInfo?.role !== 'ADMIN') {
    next('/tickets')
  } else {
    next()
  }
})

export default router
