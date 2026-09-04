import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/', redirect: '/dashboard' },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/chat/:id',
      name: 'chat',
      component: () => import('@/views/ChatView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/reports',
      name: 'report-archive',
      component: () => import('@/views/ReportArchiveView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/reports/:id',
      name: 'report',
      component: () => import('@/views/ReportView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/changes',
      name: 'changes',
      component: () => import('@/views/ChangesView.vue'),
      meta: { requiresAuth: true },
    },
    // 규제 검수는 '규제 변경 사항' 화면의 탭으로 합쳤다 — 기존 링크·북마크는 여기로 흡수한다
    { path: '/admin/review', redirect: { name: 'changes', query: { tab: 'review' } } },
    // 잘못된 주소는 대시보드로 (404 방지)
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('rai_access_token')
  if (to.meta.requiresAuth && !token) return { name: 'login' }
  if (to.name === 'login' && token) return { name: 'dashboard' }
})

export default router
