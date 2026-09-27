<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ExternalLink, RefreshCw, Search } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, formatDate } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const filters = reactive({ taskNo: '', meterNo: '', imageStatus: '' })
const loading = ref(false)
const columns: Column[] = [
  { key: 'taskNo', label: '任务编号' }, { key: 'meterNo', label: '表号' },
  { key: 'originalName', label: '文件名' }, { key: 'uploaderName', label: '上传者' },
  { key: 'imageStatusName', label: '图片状态', badge: true }, { key: 'storageStatusName', label: '存储状态', badge: true },
  { key: 'createdAt', label: '上传时间', format: formatDate },
]
async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/admin/meter-images', params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}
async function openImage(row: RowData) {
  try {
    const data = await request<RowData>({ url: `/api/v1/meter-images/${row.imageId}/access-url` })
    window.open(data.accessUrl, '_blank', 'noopener,noreferrer')
  } catch (error) { notify((error as Error).message, 'error') }
}
async function changeStatus(row: RowData) {
  const invalid = row.imageStatus === 'VALID'
  const reason = window.prompt(invalid ? '请输入标记无效的原因' : '请输入恢复原因')?.trim()
  if (!reason) return
  try {
    await request({ url: `/api/v1/admin/meter-images/${row.imageId}/${invalid ? 'invalidate' : 'restore'}`, method: 'PUT', data: { version: row.version, reason } })
    notify(invalid ? '图片已标记为无效' : '图片已恢复', 'success'); load()
  } catch (error) { notify((error as Error).message, 'error') }
}
onMounted(() => load())
</script>

<template><div class="page"><PageHeader eyebrow="OSS ASSETS" title="抄表图片" description="查看 OSS 图片元数据并治理异常图片。" /><form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.taskNo" placeholder="任务编号" /></div><input v-model="filters.meterNo" placeholder="表号" /><select v-model="filters.imageStatus"><option value="">全部状态</option><option value="VALID">有效</option><option value="INVALID">无效</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{taskNo:'',meterNo:'',imageStatus:''});load(1)"><RefreshCw :size="15" />重置</button></form><DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load"><template #actions="{ row }"><button class="table-button" @click="openImage(row)"><ExternalLink :size="14" />查看</button><button class="table-button" :class="{'table-button--danger':row.imageStatus==='VALID'}" @click="changeStatus(row)">{{ row.imageStatus==='VALID' ? '标记无效' : '恢复' }}</button></template></DataTable></div></template>
