import { createRouter, createWebHistory } from 'vue-router'
import { authState, loadCurrentUser } from './stores/auth'
import { getAccessToken } from './api/http'
import AppShell from './layouts/AppShell.vue'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('./views/DashboardView.vue') },
        { path: 'users', component: () => import('./views/UsersView.vue'), meta: { admin: true } },
        { path: 'meters', component: () => import('./views/MetersView.vue'), meta: { admin: true } },
        { path: 'devices', component: () => import('./views/DevicesView.vue'), meta: { admin: true } },
        { path: 'tasks', component: () => import('./views/TasksView.vue'), meta: { admin: true } },
        { path: 'alarms', component: () => import('./views/AlarmsView.vue'), meta: { admin: true } },
        { path: 'recognition', component: () => import('./views/RecognitionView.vue'), meta: { admin: true } },
        { path: 'images', component: () => import('./views/ImagesView.vue'), meta: { admin: true } },
        { path: 'logs', component: () => import('./views/LogsView.vue'), meta: { admin: true } },
        { path: 'my-tasks', component: () => import('./views/MyTasksView.vue'), meta: { roles: ['METER_READER'] } },
        { path: 'my-meters', component: () => import('./views/ResidentMetersView.vue'), meta: { roles: ['RESIDENT'] } },
        { path: 'reviews', component: () => import('./views/ReviewView.vue'), meta: { roles: ['ADMIN', 'AUDITOR'] } },
        { path: 'records', component: () => import('./views/RecordsView.vue'), meta: { roles: ['ADMIN', 'RESIDENT'] } },
        { path: 'notifications', component: () => import('./views/NotificationsView.vue') },
        { path: 'chat', component: () => import('./views/ChatView.vue'), meta: { roles: ['ADMIN', 'METER_READER', 'RESIDENT'] } },
        { path: 'profile', component: () => import('./views/ProfileView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) return getAccessToken() ? '/dashboard' : true
  if (!getAccessToken()) return '/login'
  if (!authState.user) {
    try {
      await loadCurrentUser()
    } catch {
      return '/login'
    }
  }
  if (to.meta.admin && !authState.user?.roles.includes('ADMIN')) return '/profile'
  const roles = to.meta.roles as string[] | undefined
  if (roles && !roles.some(role => authState.user?.roles.includes(role))) return '/profile'
  return true
})

export default router
