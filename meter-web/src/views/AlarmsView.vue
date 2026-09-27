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
const filters = reactive({ deviceNo: '', deviceName: '', alarmStatus: '' })
const loading = ref(false)
const columns: Column[] = [
  { key: 'deviceNo', label: '设备编号' }, { key: 'deviceName', label: '设备名称' },
  { key: 'alarmTypeName', label: '告警类型' }, { key: 'alarmStatusName', label: '状态', badge: true },
  { key: 'occurredAt', label: '发生时间', format: formatDate },
]
async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/admin/device-alarms', params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}
onMounted(() => load())
</script>

<template><div class="page"><PageHeader eyebrow="DEVICE HEALTH" title="设备告警" description="查看离线设备和恢复记录。" /><form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.deviceNo" placeholder="设备编号" /></div><input v-model="filters.deviceName" placeholder="设备名称" /><select v-model="filters.alarmStatus"><option value="">全部状态</option><option value="OPEN">未恢复</option><option value="RECOVERED">已恢复</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{deviceNo:'',deviceName:'',alarmStatus:''});load(1)"><RefreshCw :size="15" />重置</button></form><DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load" /></div></template>
