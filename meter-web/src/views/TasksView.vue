<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import ModalPanel from '../components/ModalPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, formatDate } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const filters = reactive({ keyword: '', executorType: '', taskStatus: '' })
const form = reactive({ meterId: null as number | null, executorType: 'METER_READER', executorId: null as number | null, scheduledAt: '', remark: '' })
const loading = ref(false), saving = ref(false), modalOpen = ref(false)
const statuses = { PENDING: '待执行', PROCESSING: '执行中', PENDING_REVIEW: '待审核', COMPLETED: '已完成', FAILED: '执行失败', CANCELLED: '已取消' }
const columns: Column[] = [
  { key: 'taskNo', label: '任务编号' }, { key: 'meterNo', label: '表号' },
  { key: 'executorTypeName', label: '执行方式' }, { key: 'executorName', label: '执行者' },
  { key: 'taskStatusName', label: '状态', badge: true }, { key: 'scheduledAt', label: '计划时间', format: formatDate },
]

async function load(target = page.page) {
  loading.value = true
  try { Object.assign(page, await request<PageData<RowData>>({ url: '/api/v1/admin/meter-reading-tasks', params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) })) }
  catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}

async function createTask() {
  saving.value = true
  try {
    await request({ url: '/api/v1/admin/meter-reading-tasks', method: 'POST', data: { ...form, remark: form.remark || null } })
    notify('任务创建成功', 'success'); modalOpen.value = false; load(1)
  } catch (error) { notify((error as Error).message, 'error') } finally { saving.value = false }
}

async function retry(row: RowData) {
  try { await request({ url: `/api/v1/admin/meter-reading-tasks/${row.taskId}/retry`, method: 'POST', data: { version: row.version } }); notify('任务已重新进入待执行状态', 'success'); load() }
  catch (error) { notify((error as Error).message, 'error') }
}

async function cancel(row: RowData) {
  const reason = window.prompt('请输入取消原因')?.trim()
  if (!reason) return
  try { await request({ url: `/api/v1/admin/meter-reading-tasks/${row.taskId}/cancel`, method: 'PUT', data: { version: row.version, reason } }); notify('任务已取消', 'success'); load() }
  catch (error) { notify((error as Error).message, 'error') }
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="READING WORKFLOW" title="抄表任务" description="创建任务并跟踪执行、识别和审核状态。"><button class="primary-button" @click="modalOpen=true"><Plus :size="17" />创建任务</button></PageHeader>
    <form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.keyword" placeholder="任务编号、表号或名称" /></div><select v-model="filters.executorType"><option value="">全部执行方式</option><option value="METER_READER">人工抄表</option><option value="DEVICE">设备采集</option></select><select v-model="filters.taskStatus"><option value="">全部状态</option><option v-for="(name,key) in statuses" :key="key" :value="key">{{ name }}</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{keyword:'',executorType:'',taskStatus:''});load(1)"><RefreshCw :size="15" />重置</button></form>
    <DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load"><template #actions="{ row }"><button v-if="row.taskStatus === 'FAILED'" class="table-button" @click="retry(row)">重试</button><button v-if="['PENDING','PROCESSING','FAILED'].includes(row.taskStatus)" class="table-button table-button--danger" @click="cancel(row)">取消</button></template></DataTable>
    <ModalPanel :open="modalOpen" title="创建抄表任务" @close="modalOpen=false"><form class="form-grid" @submit.prevent="createTask"><label><span>表具 ID</span><input v-model.number="form.meterId" type="number" min="1" required /></label><label><span>执行方式</span><select v-model="form.executorType"><option value="METER_READER">人工抄表</option><option value="DEVICE">设备自动采集</option></select></label><label><span>{{ form.executorType === 'DEVICE' ? '设备 ID' : '抄表员 ID' }}</span><input v-model.number="form.executorId" type="number" min="1" required /></label><label><span>计划执行时间</span><input v-model="form.scheduledAt" type="datetime-local" required /></label><label class="span-2"><span>备注</span><textarea v-model="form.remark" rows="3" maxlength="500" /></label><div class="form-actions span-2"><button type="button" class="secondary-button" @click="modalOpen=false">取消</button><button class="primary-button" :disabled="saving">{{ saving ? '保存中' : '创建任务' }}</button></div></form></ModalPanel>
  </div>
</template>
