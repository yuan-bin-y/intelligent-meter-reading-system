<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { AlertTriangle, BrainCircuit, CheckCircle2, ClipboardList, Gauge, RadioTower, RefreshCw, Users } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { request } from '../api/http'
import { authState } from '../stores/auth'
import type { PageData, RowData } from '../types'

const loading = ref(false)
const totals = ref({ users: 0, meters: 0, devices: 0, tasks: 0, alarms: 0, recognition: 0 })
const recentTasks = ref<RowData[]>([])

const cards = computed(() => [
  { label: '系统用户', value: totals.value.users, icon: Users, tone: 'teal' },
  { label: '表具档案', value: totals.value.meters, icon: Gauge, tone: 'ochre' },
  { label: '采集设备', value: totals.value.devices, icon: RadioTower, tone: 'blue' },
  { label: '抄表任务', value: totals.value.tasks, icon: ClipboardList, tone: 'slate' },
  { label: '未恢复告警', value: totals.value.alarms, icon: AlertTriangle, tone: 'red' },
  { label: 'AI 识别任务', value: totals.value.recognition, icon: BrainCircuit, tone: 'violet' },
])

async function load() {
  if (!authState.user?.roles.includes('ADMIN')) return
  loading.value = true
  const calls = [
    request<PageData<RowData>>({ url: '/api/v1/admin/users', params: { page: 1, pageSize: 1 } }),
    request<PageData<RowData>>({ url: '/api/v1/admin/meters', params: { page: 1, pageSize: 1 } }),
    request<PageData<RowData>>({ url: '/api/v1/admin/devices', params: { page: 1, pageSize: 1 } }),
    request<PageData<RowData>>({ url: '/api/v1/admin/meter-reading-tasks', params: { page: 1, pageSize: 5 } }),
    request<PageData<RowData>>({ url: '/api/v1/admin/device-alarms', params: { page: 1, pageSize: 1, alarmStatus: 'OPEN' } }),
    request<PageData<RowData>>({ url: '/api/v1/admin/recognition-tasks', params: { page: 1, pageSize: 1 } }),
  ]
  const result = await Promise.allSettled(calls)
  const total = (index: number) => result[index].status === 'fulfilled' ? result[index].value.total : 0
  totals.value = { users: total(0), meters: total(1), devices: total(2), tasks: total(3), alarms: total(4), recognition: total(5) }
  if (result[3].status === 'fulfilled') recentTasks.value = result[3].value.records
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="OPERATIONS / TODAY" :title="`早上好，${authState.user?.displayName}`" description="这里汇总当前系统的关键运行数据。">
      <button class="secondary-button" :disabled="loading" @click="load"><RefreshCw :size="17" :class="{ spin: loading }" />刷新</button>
    </PageHeader>

    <template v-if="authState.user?.roles.includes('ADMIN')">
      <section class="metric-grid">
        <article v-for="card in cards" :key="card.label" class="metric-card" :class="`metric-card--${card.tone}`">
          <div><span>{{ card.label }}</span><strong>{{ card.value.toLocaleString() }}</strong></div><component :is="card.icon" :size="24" />
        </article>
      </section>
      <section class="dashboard-grid">
        <article class="panel panel--wide">
          <header class="panel__header"><div><span class="eyebrow">LATEST TASKS</span><h2>近期抄表任务</h2></div><RouterLink to="/tasks">查看全部</RouterLink></header>
          <div v-if="recentTasks.length" class="task-list">
            <div v-for="task in recentTasks" :key="task.taskId" class="task-row">
              <div class="task-row__icon"><Gauge :size="18" /></div>
              <div><strong>{{ task.meterName || task.meterNo }}</strong><span>{{ task.taskNo }} · {{ task.executorName || '待分配' }}</span></div>
              <span class="status-pill status-pill--neutral">{{ task.taskStatusName }}</span>
              <time>{{ String(task.scheduledAt || '').replace('T', ' ').slice(0, 16) }}</time>
            </div>
          </div>
          <div v-else class="empty-panel"><ClipboardList :size="28" /><span>暂无抄表任务</span></div>
        </article>
        <article class="panel health-panel">
          <span class="eyebrow">SYSTEM HEALTH</span><h2>服务状态</h2>
          <div class="health-score"><span>稳定</span><strong>运行中</strong></div>
          <ul><li><CheckCircle2 :size="16" />Java API <b>8070</b></li><li><CheckCircle2 :size="16" />权限与会话 <b>正常</b></li><li><CheckCircle2 :size="16" />业务数据库 <b>已连接</b></li></ul>
        </article>
      </section>
    </template>
    <section v-else class="panel welcome-panel"><CheckCircle2 :size="36" /><h2>登录成功</h2><p>当前身份：{{ authState.user?.roles.join('、') }}</p><RouterLink class="primary-button" to="/profile">查看账户信息</RouterLink></section>
  </div>
</template>
