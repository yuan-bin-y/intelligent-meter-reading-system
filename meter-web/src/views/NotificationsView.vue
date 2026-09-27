<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { CheckCheck, RefreshCw } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { formatDate } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const unreadOnly = ref(false)
const loading = ref(false)
const columns: Column[] = [
  { key: 'notificationTypeName', label: '类型' }, { key: 'title', label: '标题' },
  { key: 'content', label: '内容' }, { key: 'actorDisplayName', label: '触发人' },
  { key: 'read', label: '状态', badge: true, format: value => value ? '已读' : '未读' },
  { key: 'createdAt', label: '时间', format: formatDate },
]
async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/notifications', params: { page: target, pageSize: page.pageSize, unreadOnly: unreadOnly.value } })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}
async function markRead(row: RowData) {
  try { await request({ url: `/api/v1/notifications/${row.notificationId}/read`, method: 'PUT' }); load() }
  catch (error) { notify((error as Error).message, 'error') }
}
async function markAll() {
  try { await request({ url: '/api/v1/notifications/read-all', method: 'PUT' }); notify('全部通知已标记为已读', 'success'); load(1) }
  catch (error) { notify((error as Error).message, 'error') }
}
onMounted(() => load())
</script>

<template><div class="page"><PageHeader eyebrow="INBOX" title="消息通知" description="集中查看任务、告警与审核进度。"><button class="secondary-button" @click="markAll"><CheckCheck :size="16" />全部已读</button></PageHeader><form class="filter-bar" @submit.prevent="load(1)"><label class="inline-check"><input v-model="unreadOnly" type="checkbox" />只看未读</label><button class="secondary-button">筛选</button><button type="button" class="text-button" @click="unreadOnly=false;load(1)"><RefreshCw :size="15" />重置</button></form><DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load"><template #actions="{ row }"><button v-if="!row.read" class="table-button" @click="markRead(row)">标为已读</button></template></DataTable></div></template>
