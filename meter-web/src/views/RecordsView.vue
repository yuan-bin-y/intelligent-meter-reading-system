<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RefreshCw, Search } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import PageHeader from '../components/PageHeader.vue'
import { authState } from '../stores/auth'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, formatDate } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const filters = reactive({ keyword: '', sourceType: '' })
const loading = ref(false)
const isAdmin = computed(() => authState.user?.roles.includes('ADMIN'))
const columns: Column[] = [
  { key: 'taskNo', label: '任务编号' }, { key: 'meterNo', label: '表号' },
  { key: 'meterName', label: '表具名称' }, { key: 'readingValue', label: '正式读数' },
  { key: 'unit', label: '单位' }, { key: 'sourceTypeName', label: '来源' },
  { key: 'readingAt', label: '抄表时间', format: formatDate },
]

async function load(target = page.page) {
  loading.value = true
  try {
    const url = isAdmin.value ? '/api/v1/admin/meter-reading-records' : '/api/v1/resident/meter-reading-records'
    Object.assign(page, await request<PageData<RowData>>({ url, params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) }))
  } catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}
onMounted(() => load())
</script>

<template><div class="page"><PageHeader eyebrow="READING ARCHIVE" title="正式抄表记录" description="查看审核通过后生成的不可修改记录。" /><form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.keyword" placeholder="任务编号、表号或名称" /></div><select v-model="filters.sourceType"><option value="">全部来源</option><option value="METER_READER">人工抄表</option><option value="DEVICE">设备采集</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{keyword:'',sourceType:''});load(1)"><RefreshCw :size="15" />重置</button></form><DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load" /></div></template>
