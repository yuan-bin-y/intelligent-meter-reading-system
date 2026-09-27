<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RefreshCw, Search } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, formatDate } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const filters = reactive({ traceId: '', operatorUsername: '', module: '', result: '' })
const loading = ref(false)
const columns: Column[] = [
  { key: 'traceId', label: 'Trace ID' }, { key: 'operatorUsername', label: '操作人' },
  { key: 'module', label: '模块' }, { key: 'action', label: '操作' },
  { key: 'resultName', label: '结果', badge: true }, { key: 'durationMs', label: '耗时', format: value => `${value ?? 0} ms` },
  { key: 'createdAt', label: '操作时间', format: formatDate },
]
async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/admin/operation-logs', params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}
onMounted(() => load())
</script>

<template><div class="page"><PageHeader eyebrow="AUDIT TRAIL" title="操作日志" description="按追踪标识定位后台操作与失败原因。" /><form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.traceId" placeholder="Trace ID" /></div><input v-model="filters.operatorUsername" placeholder="操作人" /><input v-model="filters.module" placeholder="业务模块" /><select v-model="filters.result"><option value="">全部结果</option><option value="SUCCESS">成功</option><option value="FAILURE">失败</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{traceId:'',operatorUsername:'',module:'',result:''});load(1)"><RefreshCw :size="15" />重置</button></form><DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load" /></div></template>
