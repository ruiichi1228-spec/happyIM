import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    // 管理后台（独立路由）
    { path: '/admin', redirect: '/admin/login' },
    {
      path: '/admin/login',
      name: 'AdminLogin',
      component: () => import('@/pages/AdminLogin.vue')
    },
    {
      path: '/admin/dashboard',
      name: 'AdminDashboard',
      component: () => import('@/pages/AdminDashboard.vue')
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/pages/LoginPage.vue')
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/pages/RegisterPage.vue')
    },
    {
      path: '/reset-password',
      name: 'ResetPassword',
      component: () => import('@/pages/ResetPasswordPage.vue')
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('@/pages/ChatPage.vue')
        },
        {
          path: 'contacts',
          name: 'Contacts',
          component: () => import('@/pages/ContactsPage.vue')
        },
        {
          path: 'contacts/:userId',
          name: 'ContactDetail',
          component: () => import('@/pages/ContactsPage.vue')
        },
        {
          path: 'files',
          name: 'Files',
          component: () => import('@/pages/FilesPage.vue')
        },
      ]
    }
  ]
})

export default router
