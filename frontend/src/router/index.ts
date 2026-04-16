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
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/Knowledge.vue')
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
