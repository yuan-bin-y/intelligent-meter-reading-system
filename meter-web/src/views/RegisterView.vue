<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Activity, ArrowLeft, ArrowRight, BadgeCheck, LockKeyhole, UserRound } from 'lucide-vue-next'
import { request } from '../api/http'
import { notify } from '../stores/toast'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', displayName: '', password: '', confirmPassword: '' })

async function submit() {
  if (form.password !== form.confirmPassword) return notify('两次输入的密码不一致', 'error')
  loading.value = true
  try {
    await request({
      url: '/api/v1/auth/register',
      method: 'POST',
      data: { username: form.username.trim(), displayName: form.displayName.trim(), password: form.password },
    }, false)
    notify('注册成功，请登录', 'success')
    router.replace('/login')
  } catch (error) { notify((error as Error).message, 'error') } finally { loading.value = false }
}
</script>

<template><main class="login-page"><section class="login-story"><div class="login-brand"><Activity :size="24" /><span>衡读 · 智能抄表</span></div><div class="login-story__copy"><span class="eyebrow eyebrow--light">RESIDENT ACCESS</span><h1>绑定每一块表<br><em>看见每次变化</em></h1><p>注册居民账号后，可查看本人表具、正式抄表记录、通知并发起在线沟通。</p></div><div class="meter-orbit" aria-hidden="true"><i /><i /><i /><span>24<small>H</small></span></div><div class="login-metric"><span>居民服务</span><strong>AVAILABLE</strong></div></section><section class="login-form-wrap"><form class="login-form" @submit.prevent="submit"><header><span class="eyebrow">CREATE ACCOUNT</span><h2>注册居民账号</h2><p>注册完成后使用新账号登录</p></header><label><span>用户名</span><div class="input-shell"><UserRound :size="18" /><input v-model="form.username" maxlength="64" autocomplete="username" required placeholder="请输入用户名" /></div></label><label><span>显示名称</span><div class="input-shell"><BadgeCheck :size="18" /><input v-model="form.displayName" maxlength="64" required placeholder="请输入姓名或称呼" /></div></label><label><span>密码</span><div class="input-shell"><LockKeyhole :size="18" /><input v-model="form.password" type="password" minlength="8" maxlength="128" autocomplete="new-password" required placeholder="至少 8 位" /></div></label><label><span>确认密码</span><div class="input-shell"><LockKeyhole :size="18" /><input v-model="form.confirmPassword" type="password" minlength="8" maxlength="128" autocomplete="new-password" required placeholder="再次输入密码" /></div></label><button class="primary-button login-submit" :disabled="loading"><span v-if="loading" class="loader loader--light" />{{ loading ? '正在注册' : '创建账号' }}<ArrowRight v-if="!loading" :size="18" /></button><p class="login-help"><RouterLink to="/login"><ArrowLeft :size="13" />返回登录</RouterLink></p></form></section></main></template>
