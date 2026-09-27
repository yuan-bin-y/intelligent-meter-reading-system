<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RefreshCw, Search } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, displayTypes, formatDate, meterTypes } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const keyword = ref('')
const loading = ref(false)
const columns: Column[] = [
  { key: 'meterNo', label: '表号' }, { key: 'meterName', label: '表具名称' },
  { key: 'meterType', label: '类型', format: value => meterTypes[String(value)] ?? String(value ?? '—') },
  { key: 'displayType', label: '显示形式', format: value => displayTypes[String(value)] ?? String(value ?? '—') },
  { key: 'unit', label: '单位' }, { key: 'boundAt', label: '绑定时间', format: formatDate },
]
async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/resident/meters', params: cleanParams({ page: target, pageSize: page.pageSize, keyword: keyword.value }) })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}
onMounted(() => load())
</script>

<template><div class="page"><PageHeader eyebrow="MY METERS" title="我的表具" description="查看当前账号绑定的水、电、燃气等表具。" /><form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="keyword" placeholder="表号或名称" /></div><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="keyword='';load(1)"><RefreshCw :size="15" />重置</button></form><DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load" /></div></template>
