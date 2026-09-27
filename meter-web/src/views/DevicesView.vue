<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { KeyRound, Plus, RefreshCw, Search } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import ModalPanel from '../components/ModalPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, deviceTypes } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const filters = reactive({ keyword: '', deviceType: '', status: '' })
const form = reactive({ deviceNo: '', deviceName: '', deviceType: 'CAMERA', remark: '' })
const loading = ref(false), saving = ref(false), modalOpen = ref(false)
const issuedSecret = ref<{ deviceNo: string; deviceSecret: string } | null>(null)
const columns: Column[] = [
  { key: 'deviceNo', label: '设备编号' }, { key: 'deviceName', label: '设备名称' },
  { key: 'deviceType', label: '类型', format: (v) => deviceTypes[v] || v },
  { key: 'status', label: '管理状态', badge: true, format: (v) => v === 1 ? '启用' : '停用' },
  { key: 'version', label: '版本' },
]

async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/admin/devices', params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}

async function createDevice() {
  saving.value = true
  try {
    const result = await request<RowData>({ url: '/api/v1/admin/devices', method: 'POST', data: { ...form, remark: form.remark || null } })
    issuedSecret.value = { deviceNo: result.deviceNo, deviceSecret: result.deviceSecret }
    modalOpen.value = false; notify('设备创建成功，请保存密钥', 'success'); load(1)
  } catch (error) { notify((error as Error).message, 'error') } finally { saving.value = false }
}

async function toggle(row: RowData) {
  const enable = row.status !== 1
  try {
    await request({ url: `/api/v1/admin/devices/${row.deviceId}/${enable ? 'enable' : 'disable'}`, method: 'PUT', data: { version: row.version } })
    notify(enable ? '设备已启用' : '设备已停用', 'success'); load()
  } catch (error) { notify((error as Error).message, 'error') }
}

async function copySecret() {
  if (!issuedSecret.value) return
  await navigator.clipboard.writeText(issuedSecret.value.deviceSecret)
  notify('密钥已复制', 'success')
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="EDGE FLEET" title="采集设备" description="维护固定摄像头、网关和边缘设备。"><button class="primary-button" @click="modalOpen=true"><Plus :size="17" />新增设备</button></PageHeader>
    <form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.keyword" placeholder="设备编号或名称" /></div><select v-model="filters.deviceType"><option value="">全部类型</option><option v-for="(name,key) in deviceTypes" :key="key" :value="key">{{ name }}</option></select><select v-model="filters.status"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{keyword:'',deviceType:'',status:''});load(1)"><RefreshCw :size="15" />重置</button></form>
    <DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load"><template #actions="{ row }"><button class="table-button" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</button></template></DataTable>
    <ModalPanel :open="modalOpen" title="新增采集设备" @close="modalOpen=false"><form class="form-grid" @submit.prevent="createDevice"><label><span>设备编号</span><input v-model="form.deviceNo" required maxlength="64" /></label><label><span>设备名称</span><input v-model="form.deviceName" required maxlength="64" /></label><label class="span-2"><span>设备类型</span><select v-model="form.deviceType"><option v-for="(name,key) in deviceTypes" :key="key" :value="key">{{ name }}</option></select></label><label class="span-2"><span>备注</span><textarea v-model="form.remark" rows="3" maxlength="500" /></label><div class="form-actions span-2"><button type="button" class="secondary-button" @click="modalOpen=false">取消</button><button class="primary-button" :disabled="saving">{{ saving ? '保存中' : '创建设备' }}</button></div></form></ModalPanel>
    <ModalPanel :open="!!issuedSecret" title="保存设备密钥" @close="issuedSecret=null"><div class="secret-box"><KeyRound :size="24" /><p>密钥只在创建时返回一次，请立即保存。</p><code>{{ issuedSecret?.deviceSecret }}</code><button class="primary-button" @click="copySecret">复制密钥</button></div></ModalPanel>
  </div>
</template>
