<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, RefreshCw, Search } from 'lucide-vue-next'
import { request } from '../api/http'
import DataTable, { type Column } from '../components/DataTable.vue'
import ModalPanel from '../components/ModalPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { cleanParams, formatDate, userStatuses } from '../utils/format'

const page = reactive({ records: [] as RowData[], total: 0, page: 1, pageSize: 20 })
const filters = reactive({ username: '', status: '', roleCode: '' })
const loading = ref(false)
const modalOpen = ref(false)
const saving = ref(false)
const form = reactive({ username: '', password: '', displayName: '', status: 1, roles: ['RESIDENT'] as string[] })
const roles = ['ADMIN', 'METER_READER', 'AUDITOR', 'RESIDENT']
const columns: Column[] = [
  { key: 'username', label: '用户名' }, { key: 'displayName', label: '显示名称' },
  { key: 'roles', label: '角色', format: (v) => (v || []).join('、') },
  { key: 'status', label: '状态', badge: true, format: (v) => userStatuses[v] || v },
  { key: 'createdAt', label: '创建时间', format: formatDate },
]

async function load(target = page.page) {
  loading.value = true
  try {
    const data = await request<PageData<RowData>>({ url: '/api/v1/admin/users', params: cleanParams({ ...filters, page: target, pageSize: page.pageSize }) })
    Object.assign(page, data)
  } catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}

async function createUser() {
  saving.value = true
  try {
    await request({ url: '/api/v1/admin/users', method: 'POST', data: form })
    notify('用户创建成功', 'success'); modalOpen.value = false
    Object.assign(form, { username: '', password: '', displayName: '', status: 1, roles: ['RESIDENT'] })
    load(1)
  } catch (error) { notify((error as Error).message, 'error') } finally { saving.value = false }
}

async function toggleStatus(row: RowData) {
  try {
    await request({ url: `/api/v1/admin/users/${row.userId}/status`, method: 'PUT', data: { status: row.status === 1 ? 0 : 1 } })
    notify(row.status === 1 ? '用户已禁用' : '用户已启用', 'success'); load()
  } catch (error) { notify((error as Error).message, 'error') }
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="IDENTITY" title="用户管理" description="管理账号状态和系统角色。"><button class="primary-button" @click="modalOpen = true"><Plus :size="17" />新增用户</button></PageHeader>
    <form class="filter-bar" @submit.prevent="load(1)"><div class="input-shell"><Search :size="17" /><input v-model="filters.username" placeholder="搜索用户名" /></div><select v-model="filters.roleCode"><option value="">全部角色</option><option v-for="role in roles" :key="role">{{ role }}</option></select><select v-model="filters.status"><option value="">全部状态</option><option value="1">启用</option><option value="0">禁用</option></select><button class="secondary-button"><Search :size="16" />查询</button><button type="button" class="text-button" @click="Object.assign(filters,{username:'',status:'',roleCode:''});load(1)"><RefreshCw :size="15" />重置</button></form>
    <DataTable :rows="page.records" :columns="columns" :loading="loading" :total="page.total" :page="page.page" :page-size="page.pageSize" @page="load"><template #actions="{ row }"><button class="table-button" @click="toggleStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</button></template></DataTable>
    <ModalPanel :open="modalOpen" title="新增用户" @close="modalOpen = false"><form class="form-grid" @submit.prevent="createUser"><label><span>用户名</span><input v-model="form.username" required maxlength="64" /></label><label><span>显示名称</span><input v-model="form.displayName" required maxlength="64" /></label><label class="span-2"><span>初始密码</span><input v-model="form.password" type="password" required minlength="8" maxlength="128" /></label><fieldset class="span-2"><legend>角色</legend><label v-for="role in roles" :key="role" class="check-option"><input v-model="form.roles" type="checkbox" :value="role" />{{ role }}</label></fieldset><div class="form-actions span-2"><button type="button" class="secondary-button" @click="modalOpen=false">取消</button><button class="primary-button" :disabled="saving">{{ saving ? '保存中' : '创建用户' }}</button></div></form></ModalPanel>
  </div>
</template>
