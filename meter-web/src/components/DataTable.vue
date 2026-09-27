<script setup lang="ts">
import { ChevronLeft, ChevronRight, Inbox } from 'lucide-vue-next'
import type { RowData } from '../types'

export interface Column {
  key: string
  label: string
  width?: string
  format?: (value: any, row: RowData) => string
  badge?: boolean
}

const props = defineProps<{
  rows: RowData[]
  columns: Column[]
  loading?: boolean
  total?: number
  page?: number
  pageSize?: number
}>()

const emit = defineEmits<{ page: [page: number]; rowClick: [row: RowData] }>()

function rowKey(row: RowData, index: number) {
  return row.id ?? row.userId ?? row.meterId ?? row.deviceId ?? row.taskId
    ?? row.recognitionTaskId ?? row.resultId ?? row.recordId ?? row.imageId
    ?? row.alarmId ?? row.logId ?? row.notificationId ?? row.conversationId ?? index
}

function badgeTone(value: unknown) {
  const text = String(value ?? '')
  if (/成功|完成|正常|启用|在线|SUCCEEDED|COMPLETED|ACTIVE|ENABLED|RECOVERED/.test(text)) return 'good'
  if (/失败|离线|禁用|取消|FAILED|OFFLINE|DISABLED|CANCELLED/.test(text)) return 'bad'
  if (/处理中|执行中|识别中|维护|PROCESSING|PENDING_REVIEW/.test(text)) return 'warn'
  return 'neutral'
}
</script>

<template>
  <div class="table-card">
    <div class="table-scroll">
      <table>
        <thead><tr><th v-for="col in columns" :key="col.key" :style="{ width: col.width }">{{ col.label }}</th><th v-if="$slots.actions">操作</th></tr></thead>
        <tbody>
          <tr v-if="loading"><td :colspan="columns.length + ($slots.actions ? 1 : 0)"><div class="table-state"><span class="loader" />正在读取数据</div></td></tr>
          <tr v-else-if="!rows.length"><td :colspan="columns.length + ($slots.actions ? 1 : 0)"><div class="table-state"><Inbox :size="22" />暂无数据</div></td></tr>
          <tr v-for="(row, index) in rows" v-else :key="rowKey(row, index)" @dblclick="emit('rowClick', row)">
            <td v-for="col in columns" :key="col.key">
              <span v-if="col.badge" class="status-pill" :class="`status-pill--${badgeTone(col.format ? col.format(row[col.key], row) : row[col.key])}`">{{ col.format ? col.format(row[col.key], row) : row[col.key] }}</span>
              <template v-else>{{ col.format ? col.format(row[col.key], row) : (row[col.key] ?? '—') }}</template>
            </td>
            <td v-if="$slots.actions" class="table-actions"><slot name="actions" :row="row" /></td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer v-if="total !== undefined && total > 0" class="pagination">
      <span>共 {{ total }} 条</span>
      <div>
        <button class="icon-button" :disabled="(page ?? 1) <= 1" @click="emit('page', (page ?? 1) - 1)"><ChevronLeft :size="17" /></button>
        <b>{{ page ?? 1 }}</b><span>/</span><span>{{ Math.max(1, Math.ceil(total / (pageSize ?? 20))) }}</span>
        <button class="icon-button" :disabled="(page ?? 1) >= Math.ceil(total / (pageSize ?? 20))" @click="emit('page', (page ?? 1) + 1)"><ChevronRight :size="17" /></button>
      </div>
    </footer>
  </div>
</template>
