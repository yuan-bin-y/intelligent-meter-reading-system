<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import ModalPanel from '../components/ModalPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, displayTypes, meterStatuses, meterTypes } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const filters = reactive({ keyword: '', meterType: '', displayType: '', status: '' })
const form = reactive({ meterNo: '', meterName: '', meterType: 'ELECTRIC', displayType: 'LCD', unit: 'kWh', integerDigits: 6, decimalDigits: 2, initialReading: 0, installedAt: '', remark: '' })
const loading = ref(false), saving = ref(false), modalOpen = ref(false)
const columns: Column[] = [
  { key: 'meterNo', label: '表号' }, { key: 'meterName', label: '表具名称' },
  { key: 'meterType', label: '类型', format: (v) => meterTypes[v] || v },
  { key: 'displayType', label: '显示方式', format: (v) => displayTypes[v] || v },
  { key: 'unit', label: '单位' }, { key: 'status', label: '状态', badge: true, format: (v) => meterStatuses[v] || v },
]

async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/admin/meters', params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}

function syncUnit() { form.unit = form.meterType === 'ELECTRIC' ? 'kWh' : 'm³' }

async function createMeter() {
  saving.value = true
  try {
    await request({ url: '/api/v1/admin/meters', method: 'POST', data: { ...form, installedAt: form.installedAt || null, remark: form.remark || null } })
    notify('表具创建成功', 'success'); modalOpen.value = false; load(1)
  } catch (error) { notify((error as Error).message, 'error') } finally { saving.value = false }
}

async function changeStatus(row: RowData) {
  const status = row.status === 1 ? 0 : 1
  try {
    await request({ url: `/api/v1/admin/meters/${row.meterId}/status`, method: 'PUT', data: { status, version: row.version } })
    notify(status ? '表具已启用' : '表具已停用', 'success'); load()
  } catch (error) { notify((error as Error).message, 'error') }
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="ASSET REGISTER" title="表具档案" description="维护表号、显示方式和生命周期状态。"><button class="primary-button" @click="modalOpen=true"><Plus :size="17" />新增表具</button></PageHeader>
    <form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.keyword" placeholder="表号或名称" /></div><select v-model="filters.meterType"><option value="">全部类型</option><option v-for="(name,key) in meterTypes" :key="key" :value="key">{{ name }}</option></select><select v-model="filters.displayType"><option value="">全部显示方式</option><option v-for="(name,key) in displayTypes" :key="key" :value="key">{{ name }}</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{keyword:'',meterType:'',displayType:'',status:''});load(1)"><RefreshCw :size="15" />重置</button></form>
    <DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load"><template #actions="{ row }"><button v-if="row.status !== 3" class="table-button" @click="changeStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</button></template></DataTable>
    <ModalPanel :open="modalOpen" title="新增表具" @close="modalOpen=false"><form class="form-grid" @submit.prevent="createMeter"><label><span>表号</span><input v-model="form.meterNo" required maxlength="64" /></label><label><span>表具名称</span><input v-model="form.meterName" required maxlength="64" /></label><label><span>表具类型</span><select v-model="form.meterType" @change="syncUnit"><option v-for="(name,key) in meterTypes" :key="key" :value="key">{{ name }}</option></select></label><label><span>显示方式</span><select v-model="form.displayType"><option v-for="(name,key) in displayTypes" :key="key" :value="key">{{ name }}</option></select></label><label><span>计量单位</span><input v-model="form.unit" required /></label><label><span>初始读数</span><input v-model.number="form.initialReading" type="number" min="0" step="0.001" required /></label><label><span>整数位数</span><input v-model.number="form.integerDigits" type="number" min="1" max="12" required /></label><label><span>小数位数</span><input v-model.number="form.decimalDigits" type="number" min="0" max="3" required /></label><label class="span-2"><span>安装日期</span><input v-model="form.installedAt" type="date" /></label><label class="span-2"><span>备注</span><textarea v-model="form.remark" rows="3" maxlength="500" /></label><div class="form-actions span-2"><button type="button" class="secondary-button" @click="modalOpen=false">取消</button><button class="primary-button" :disabled="saving">{{ saving ? '保存中' : '创建表具' }}</button></div></form></ModalPanel>
  </div>
</template>
