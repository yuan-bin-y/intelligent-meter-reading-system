<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Camera, Play, RefreshCw } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import ModalPanel from '../components/ModalPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, formatDate } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const status = ref('')
const loading = ref(false)
const modalOpen = ref(false)
const submitting = ref(false)
const selected = ref<RowData | null>(null)
const form = reactive({ readingValue: null as number | null, remark: '', file: null as File | null })
const columns: Column[] = [
  { key: 'taskNo', label: '任务编号' }, { key: 'meterNo', label: '表号' },
  { key: 'meterName', label: '表具名称' }, { key: 'taskStatusName', label: '状态', badge: true },
  { key: 'scheduledAt', label: '计划时间', format: formatDate },
]

async function load(target = page.page) {
  loading.value = true
  try {
    Object.assign(page, await request<PageData<RowData>>({
      url: '/api/v1/meter-reader/tasks',
      params: cleanParams({ page: target, pageSize: page.pageSize, taskStatus: status.value }),
    }))
  } catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}

async function start(row: RowData) {
  try {
    await request({ url: `/api/v1/meter-reader/tasks/${row.taskId}/start`, method: 'PUT', data: { version: row.version } })
    notify('任务已开始', 'success')
    load()
  } catch (error) { notify((error as Error).message, 'error') }
}

function openSubmit(row: RowData) {
  selected.value = row
  Object.assign(form, { readingValue: null, remark: '', file: null })
  modalOpen.value = true
}

function chooseFile(event: Event) {
  form.file = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function submitResult() {
  if (!selected.value || !form.file || form.readingValue === null) return
  submitting.value = true
  try {
    const body = new FormData()
    body.append('file', form.file)
    const image = await request<RowData>({
      url: `/api/v1/meter-reader/tasks/${selected.value.taskId}/images`,
      method: 'POST',
      data: body,
      params: { imageType: 'ORIGINAL' },
      headers: { 'X-Idempotency-Key': crypto.randomUUID().replaceAll('-', '') },
    })
    await request({
      url: `/api/v1/meter-reader/tasks/${selected.value.taskId}/result`,
      method: 'POST',
      data: { version: selected.value.version, readingValue: form.readingValue, imageIds: [image.imageId], remark: form.remark || null },
    })
    notify('读数和现场图片已提交审核', 'success')
    modalOpen.value = false
    load()
  } catch (error) { notify((error as Error).message, 'error') } finally { submitting.value = false }
}

onMounted(() => load())
</script>

<template><div class="page"><PageHeader eyebrow="FIELD WORK" title="我的抄表任务" description="开始现场任务并提交读数与照片。" /><form class="filter-bar" @submit.prevent="load(1)"><select v-model="status"><option value="">全部状态</option><option value="PENDING">待执行</option><option value="PROCESSING">执行中</option><option value="PENDING_REVIEW">待审核</option><option value="COMPLETED">已完成</option><option value="FAILED">失败</option></select><button class="secondary-button">查询</button><button type="button" class="text-button" @click="status='';load(1)"><RefreshCw :size="15" />重置</button></form><DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load"><template #actions="{ row }"><button v-if="row.taskStatus==='PENDING'" class="table-button" @click="start(row)"><Play :size="14" />开始</button><button v-if="row.taskStatus==='PROCESSING'" class="table-button" @click="openSubmit(row)"><Camera :size="14" />提交读数</button></template></DataTable><ModalPanel :open="modalOpen" title="提交人工抄表结果" @close="modalOpen=false"><form class="stack-form" @submit.prevent="submitResult"><label><span>本次读数</span><input v-model.number="form.readingValue" type="number" min="0" step="0.001" required /></label><label><span>现场照片</span><input type="file" accept="image/jpeg,image/png,image/webp" required @change="chooseFile" /></label><label><span>备注</span><textarea v-model="form.remark" rows="3" maxlength="500" /></label><div class="form-actions"><button type="button" class="secondary-button" @click="modalOpen=false">取消</button><button class="primary-button" :disabled="submitting">{{ submitting ? '上传提交中' : '提交审核' }}</button></div></form></ModalPanel></div></template>
