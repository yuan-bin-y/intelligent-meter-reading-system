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
const filters = reactive({ recognitionNo: '', meterNo: '', status: '', modelName: '' })
const loading = ref(false)
const statuses = { PENDING: '待识别', PROCESSING: '识别中', SUCCEEDED: '识别成功', FAILED: '识别失败', CANCELLED: '已取消' }
const columns: Column[] = [
  { key: 'recognitionNo', label: '识别编号' }, { key: 'meterNo', label: '表号' },
  { key: 'statusName', label: '状态', badge: true }, { key: 'recognizedValue', label: '识别读数' },
  { key: 'confidence', label: '置信度', format: (v) => v == null ? '—' : `${(Number(v) * 100).toFixed(1)}%` },
  { key: 'modelName', label: '模型' }, { key: 'completedAt', label: '完成时间', format: formatDate },
]
async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/admin/recognition-tasks', params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}
async function retry(row: RowData) {
  try { await request({ url: `/api/v1/admin/recognition-tasks/${row.recognitionTaskId}/retry`, method: 'POST', data: { version: row.version } }); notify('识别任务已重新提交', 'success'); load() }
  catch (error) { notify((error as Error).message, 'error') }
}
async function cancel(row: RowData) {
  const reason = window.prompt('请输入取消原因')?.trim(); if (!reason) return
  try { await request({ url: `/api/v1/admin/recognition-tasks/${row.recognitionTaskId}/cancel`, method: 'PUT', data: { version: row.version, reason } }); notify('识别任务已取消', 'success'); load() }
  catch (error) { notify((error as Error).message, 'error') }
}
onMounted(() => load())
</script>

<template><div class="page"><PageHeader eyebrow="VISION PIPELINE" title="AI 识别" description="查看模型读数、置信度和任务状态。" /><form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.recognitionNo" placeholder="识别任务编号" /></div><input v-model="filters.meterNo" placeholder="表号" /><select v-model="filters.status"><option value="">全部状态</option><option v-for="(name,key) in statuses" :key="key" :value="key">{{ name }}</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{recognitionNo:'',meterNo:'',status:'',modelName:''});load(1)"><RefreshCw :size="15" />重置</button></form><DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load"><template #actions="{ row }"><button v-if="row.status === 'FAILED'" class="table-button" @click="retry(row)">重试</button><button v-if="['PENDING','PROCESSING'].includes(row.status)" class="table-button table-button--danger" @click="cancel(row)">取消</button></template></DataTable></div></template>
