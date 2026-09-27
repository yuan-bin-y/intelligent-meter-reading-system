<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Activity, Bell, BellRing, BrainCircuit, ClipboardCheck, ClipboardList, FileClock, Gauge, Images, LayoutDashboard, LogOut, Menu, MessagesSquare, RadioTower, ScrollText, UserRound, Users, X } from 'lucide-vue-next'
import { authState, logout } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)

const adminItems = [
  { to: '/dashboard', label: '运行总览', icon: LayoutDashboard },
  { to: '/users', label: '用户管理', icon: Users },
  { to: '/meters', label: '表具档案', icon: Gauge },
  { to: '/devices', label: '采集设备', icon: RadioTower },
  { to: '/tasks', label: '抄表任务', icon: ClipboardList },
  { to: '/alarms', label: '设备告警', icon: BellRing },
  { to: '/recognition', label: 'AI 识别', icon: BrainCircuit },
  { to: '/images', label: '抄表图片', icon: Images },
  { to: '/reviews', label: '结果审核', icon: ClipboardCheck },
  { to: '/records', label: '正式记录', icon: ScrollText },
  { to: '/logs', label: '操作日志', icon: FileClock },
]
const items = computed(() => {
  const roles = authState.user?.roles ?? []
  if (roles.includes('ADMIN')) return [...adminItems, { to: '/chat', label: '实时沟通', icon: MessagesSquare }, { to: '/notifications', label: '消息通知', icon: Bell }]
  const result = [{ to: '/dashboard', label: '工作台', icon: LayoutDashboard }]
  if (roles.includes('METER_READER')) result.push({ to: '/my-tasks', label: '我的任务', icon: ClipboardList })
  if (roles.includes('AUDITOR')) result.push({ to: '/reviews', label: '结果审核', icon: ClipboardCheck })
  if (roles.includes('RESIDENT')) {
    result.push({ to: '/my-meters', label: '我的表具', icon: Gauge })
    result.push({ to: '/records', label: '抄表记录', icon: ScrollText })
  }
  if (roles.includes('METER_READER') || roles.includes('RESIDENT')) result.push({ to: '/chat', label: '实时沟通', icon: MessagesSquare })
  result.push({ to: '/notifications', label: '消息通知', icon: Bell })
  result.push({ to: '/profile', label: '我的账户', icon: UserRound })
  return result
})

async function signOut() {
  await logout()
  router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <button class="mobile-menu" @click="menuOpen = true"><Menu :size="21" /></button>
    <div v-if="menuOpen" class="nav-scrim" @click="menuOpen = false" />
    <aside class="sidebar" :class="{ 'sidebar--open': menuOpen }">
      <div class="brand">
        <div class="brand__mark"><Activity :size="23" stroke-width="2.4" /></div>
        <div><strong>衡读</strong><span>METER OPS</span></div>
        <button class="icon-button sidebar__close" @click="menuOpen = false"><X :size="18" /></button>
      </div>
      <div class="system-pulse"><i /><span>业务系统运行中</span><b>8070</b></div>
      <nav>
        <span class="nav-label">工作区</span>
        <RouterLink v-for="item in items" :key="item.to" :to="item.to" :class="{ active: route.path === item.to }" @click="menuOpen = false">
          <component :is="item.icon" :size="18" /><span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar__foot">
        <RouterLink to="/profile" class="user-chip">
          <span>{{ authState.user?.displayName?.slice(0, 1) }}</span>
          <div><strong>{{ authState.user?.displayName }}</strong><small>{{ authState.user?.roles.join(' · ') }}</small></div>
        </RouterLink>
        <button class="icon-button" title="退出登录" @click="signOut"><LogOut :size="18" /></button>
      </div>
    </aside>
    <main class="workspace"><RouterView /></main>
  </div>
</template>
