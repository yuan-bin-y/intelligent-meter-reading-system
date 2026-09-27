<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Client } from '@stomp/stompjs'
import { Headphones, MessageCircle, Plus, Send } from 'lucide-vue-next'
import { getAccessToken, request } from '../api/http'
import PageHeader from '../components/PageHeader.vue'
import { authState } from '../stores/auth'
import { notify } from '../stores/toast'
import type { PageData, RowData } from '../types'
import { formatDate } from '../utils/format'

const conversations = ref<RowData[]>([])
const messages = ref<RowData[]>([])
const selected = ref<RowData | null>(null)
const loading = ref(false)
const connected = ref(false)
const draft = ref('')
const messagePanel = ref<HTMLElement | null>(null)
const isAdmin = computed(() => authState.user?.roles.includes('ADMIN') ?? false)
const isResident = computed(() => authState.user?.roles.includes('RESIDENT') ?? false)
let stomp: Client | null = null

async function loadConversations() {
  loading.value = true
  try {
    const url = isAdmin.value ? '/api/v1/admin/chat/conversations' : '/api/v1/chat/conversations'
    const data = await request<PageData<RowData>>({ url, params: { page: 1, pageSize: 100 } })
    let records = data.records
    if (isAdmin.value) {
      const waiting = await request<PageData<RowData>>({ url: '/api/v1/admin/chat/conversations/waiting', params: { page: 1, pageSize: 100 } })
      const ids = new Set(records.map(item => item.conversationId))
      records = [...waiting.records.filter(item => !ids.has(item.conversationId)), ...records]
    }
    conversations.value = records
    if (selected.value) selected.value = records.find(item => item.conversationId === selected.value?.conversationId) ?? null
  } catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}

async function selectConversation(item: RowData) {
  if (item.status === 'WAITING' && isAdmin.value) return
  selected.value = item
  try {
    const data = await request<RowData>({ url: `/api/v1/chat/conversations/${item.conversationId}/messages`, params: { pageSize: 100 } })
    messages.value = [...(data.records ?? [])].sort((a, b) => a.messageId - b.messageId)
    await nextTick()
    scrollBottom()
    const last = messages.value.at(-1)
    if (last && connected.value) {
      stomp?.publish({ destination: '/app/chat.read', body: JSON.stringify({ conversationId: item.conversationId, lastReadMessageId: last.messageId }) })
    }
  } catch (error) { notify((error as Error).message, 'error') }
}

async function claim(item: RowData) {
  try {
    await request({ url: `/api/v1/admin/chat/conversations/${item.conversationId}/claim`, method: 'PUT', data: { version: item.version } })
    notify('客服会话已认领', 'success')
    await loadConversations()
    const claimed = conversations.value.find(row => row.conversationId === item.conversationId)
    if (claimed) selectConversation(claimed)
  } catch (error) { notify((error as Error).message, 'error') }
}

function connect() {
  const token = getAccessToken()
  if (!token) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  stomp = new Client({
    webSocketFactory: () => new WebSocket(`${protocol}//${location.host}/ws/chat`),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      connected.value = true
      stomp?.subscribe('/user/queue/chat', frame => {
        const message = JSON.parse(frame.body)
        if (message.conversationId === selected.value?.conversationId && !messages.value.some(item => item.messageId === message.messageId)) {
          messages.value.push(message)
          nextTick(scrollBottom)
        }
        loadConversations()
      })
      stomp?.subscribe('/user/queue/chat.errors', frame => notify(JSON.parse(frame.body).message || '消息发送失败', 'error'))
    },
    onWebSocketClose: () => { connected.value = false },
    onStompError: frame => notify(frame.headers.message || '聊天连接失败', 'error'),
  })
  stomp.activate()
}

function sendMessage() {
  const content = draft.value.trim()
  if (!content || !selected.value || !connected.value) return
  stomp?.publish({
    destination: '/app/chat.send',
    body: JSON.stringify({ conversationId: selected.value.conversationId, clientMessageId: crypto.randomUUID().replaceAll('-', ''), content }),
  })
  draft.value = ''
}

async function createTaskChat() {
  const taskId = Number(window.prompt('请输入抄表任务 ID'))
  if (!Number.isInteger(taskId) || taskId <= 0) return
  try { await request({ url: '/api/v1/chat/conversations/task', method: 'POST', data: { taskId } }); notify('任务会话已创建', 'success'); loadConversations() }
  catch (error) { notify((error as Error).message, 'error') }
}

async function createSupportChat() {
  const subject = window.prompt('请输入咨询主题')?.trim()
  if (!subject) return
  const content = window.prompt('请输入问题内容')?.trim()
  if (!content) return
  try {
    await request({ url: '/api/v1/chat/conversations/admin-support', method: 'POST', data: { subject, content, clientMessageId: crypto.randomUUID().replaceAll('-', '') } })
    notify('客服会话已创建，请等待管理员认领', 'success'); loadConversations()
  } catch (error) { notify((error as Error).message, 'error') }
}

function scrollBottom() { if (messagePanel.value) messagePanel.value.scrollTop = messagePanel.value.scrollHeight }
onMounted(() => { loadConversations(); connect() })
onBeforeUnmount(() => { stomp?.deactivate() })
</script>

<template><div class="page"><PageHeader eyebrow="REALTIME DESK" title="实时沟通" description="围绕抄表任务与居民、抄表员和管理员沟通。"><button v-if="!isAdmin" class="secondary-button" @click="createTaskChat"><Plus :size="16" />任务会话</button><button v-if="isResident" class="primary-button" @click="createSupportChat"><Headphones :size="16" />联系管理员</button></PageHeader><section class="chat-shell"><aside class="chat-list"><header><strong>会话列表</strong><span :class="{online:connected}">{{ connected ? '实时连接' : '连接中' }}</span></header><div v-if="loading" class="chat-empty">正在读取会话</div><button v-for="item in conversations" :key="item.conversationId" :class="{active:selected?.conversationId===item.conversationId}" @click="selectConversation(item)"><span class="chat-list__icon"><MessageCircle :size="17" /></span><span><strong>{{ item.subject || item.taskNo || `会话 ${item.conversationId}` }}</strong><small>{{ item.lastMessageContent || item.statusName }}</small></span><em v-if="item.unreadCount">{{ item.unreadCount }}</em><em v-if="isAdmin && item.status==='WAITING'" class="claim" @click.stop="claim(item)">认领</em></button><div v-if="!loading&&!conversations.length" class="chat-empty">暂无会话</div></aside><article class="chat-room"><template v-if="selected"><header><div><strong>{{ selected.subject || selected.taskNo }}</strong><small>{{ selected.statusName }} · {{ selected.conversationTypeName }}</small></div></header><div ref="messagePanel" class="message-list"><div v-for="message in messages" :key="message.messageId" class="message-bubble" :class="{'message-bubble--mine':message.senderId===authState.user?.userId}"><span>{{ message.senderDisplayName }}</span><p>{{ message.content }}</p><time>{{ formatDate(message.createdAt) }}</time></div><div v-if="!messages.length" class="chat-empty">还没有消息</div></div><form class="chat-compose" @submit.prevent="sendMessage"><textarea v-model="draft" rows="2" maxlength="2000" placeholder="输入消息，Ctrl + Enter 也可发送" @keydown.ctrl.enter.prevent="sendMessage" /><button class="primary-button" :disabled="!connected||!draft.trim()"><Send :size="16" />发送</button></form></template><div v-else class="chat-empty chat-empty--room"><MessageCircle :size="30" />选择一条会话开始沟通</div></article></section></div></template>
