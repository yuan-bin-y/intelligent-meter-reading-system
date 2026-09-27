<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Check, RefreshCw, Search, X } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, formatDate } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const filters = reactive({ keyword: '', reviewStatus: 'PENDING' })
const loading = ref(false)
const columns: Column[] = [
  { key: 'taskNo', label: '任务编号' }, { key: 'meterNo', label: '表号' },
  { key: 'readingValue', label: '上报读数' }, { key: 'executorName', label: '执行者' },
  { key: 'reviewStatusName', label: '审核状态', badge: true }, { key: 'submittedAt', label: '提交时间', format: formatDate },
]

async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/review/meter-reading-results', params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}

async function approve(row: RowData) {
  const entered = window.prompt('请输入最终确认读数', String(row.readingValue ?? ''))
  if (entered === null || entered.trim() === '' || Number.isNaN(Number(entered))) return
  try {
    await request({ url: `/api/v1/review/meter-reading-results/${row.resultId}/approve`, method: 'PUT', data: { resultVersion: row.resultVersion, taskVersion: row.taskVersion, confirmedReadingValue: Number(entered), remark: null } })
    notify('审核通过，正式抄表记录已生成', 'success'); load()
  } catch (error) { notify((error as Error).message, 'error') }
}

async function reject(row: RowData) {
  const reason = window.prompt('请输入驳回原因')?.trim()
  if (!reason) return
  try {
    await request({ url: `/api/v1/review/meter-reading-results/${row.resultId}/reject`, method: 'PUT', data: { resultVersion: row.resultVersion, taskVersion: row.taskVersion, reason } })
    notify('结果已驳回', 'success'); load()
  } catch (error) { notify((error as Error).message, 'error') }
}

onMounted(() => load())
</script>

<template><div class="page"><PageHeader eyebrow="QUALITY CONTROL" title="结果审核" description="确认 AI 与人工提交的抄表结果。" /><form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.keyword" placeholder="任务编号、表号或名称" /></div><select v-model="filters.reviewStatus"><option value="">全部状态</option><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{keyword:'',reviewStatus:'PENDING'});load(1)"><RefreshCw :size="15" />重置</button></form><DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load"><template #actions="{ row }"><button v-if="row.reviewStatus==='PENDING'" class="table-button" @click="approve(row)"><Check :size="14" />通过</button><button v-if="row.reviewStatus==='PENDING'" class="table-button table-button--danger" @click="reject(row)"><X :size="14" />驳回</button></template></DataTable></div></template>
