<script setup lang="ts">
import { reactive, ref } from 'vue'
import { KeyRound, ShieldCheck, UserRound } from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import { request } from '../api/http'
import { authState } from '../stores/auth'
import { notify } from '../stores/toast'

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const saving = ref(false)
async function changePassword() {
  if (form.newPassword !== form.confirmPassword) return notify('两次新密码不一致', 'error')
  saving.value = true
  try {
    await request({
      url: '/api/v1/auth/password',
      method: 'PUT',
      data: { oldPassword: form.oldPassword, newPassword: form.newPassword },
    })
    notify('密码已修改，其他会话已经失效', 'success')
    Object.assign(form, { oldPassword: '', newPassword: '', confirmPassword: '' })
  }
  catch (error) { notify((error as Error).message, 'error') } finally { saving.value = false }
}
</script>

<template><div class="page"><PageHeader eyebrow="ACCOUNT" title="我的账户" description="查看登录身份并维护密码。" /><section class="profile-grid"><article class="panel identity-card"><div class="identity-card__avatar"><UserRound :size="34" /></div><h2>{{ authState.user?.displayName }}</h2><p>@{{ authState.user?.username }}</p><div class="role-list"><span v-for="role in authState.user?.roles" :key="role"><ShieldCheck :size="14" />{{ role }}</span></div><dl><div><dt>用户 ID</dt><dd>{{ authState.user?.userId }}</dd></div><div><dt>认证方式</dt><dd>JWT + Redis Session</dd></div></dl></article><article class="panel"><header class="panel__header"><div><span class="eyebrow">SECURITY</span><h2>修改密码</h2></div><KeyRound :size="22" /></header><form class="stack-form" @submit.prevent="changePassword"><label><span>当前密码</span><input v-model="form.oldPassword" type="password" required /></label><label><span>新密码</span><input v-model="form.newPassword" type="password" minlength="8" required /></label><label><span>确认新密码</span><input v-model="form.confirmPassword" type="password" minlength="8" required /></label><button class="primary-button" :disabled="saving">{{ saving ? '提交中' : '修改密码' }}</button></form></article></section></div></template>
