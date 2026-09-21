import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { requiresAuth: false }
  },
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
        path: 'tickets/:id',
        name: 'TicketDetail',
        component: () => import('@/views/ticket/TicketDetail.vue')
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
      {
        path: 'my-tickets',
        name: 'MyTickets',
        component: () => import('@/views/ticket/MyTickets.vue')
      },
      {
        path: 'pending',
        name: 'PendingTickets',
        component: () => import('@/views/ticket/PendingTickets.vue')
      },
      {
        path: 'accepted',
        name: 'AcceptedTickets',
        component: () => import('@/views/ticket/AcceptedTickets.vue')
      },
      {
        path: 'completed',
        name: 'CompletedTickets',
        component: () => import('@/views/ticket/CompletedTickets.vue')
      },
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
    next('/login')
  } else if (to.meta.requiresAdmin && authStore.userInfo?.role !== 'ADMIN') {
    next('/tickets')
  } else if ((to.path === '/login' || to.path === '/register') && authStore.token) {
    next('/tickets')
  } else {
    next()
  }
})

export default router
