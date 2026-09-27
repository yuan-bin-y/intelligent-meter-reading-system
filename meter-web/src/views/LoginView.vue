<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Activity, ArrowRight, Eye, EyeOff, LockKeyhole, UserRound } from 'lucide-vue-next'
import { login } from '../stores/auth'
import { notify } from '../stores/toast'

const router = useRouter()
const username = ref('')
const password = ref('')
const visible = ref(false)
const loading = ref(false)

async function submit() {
  if (!username.value.trim() || !password.value) return notify('请输入用户名和密码', 'error')
  loading.value = true
  try {
    await login(username.value.trim(), password.value)
    notify('登录成功', 'success')
    router.replace('/dashboard')
  } catch (error) {
    notify(error instanceof Error ? error.message : '登录失败', 'error')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <div class="login-brand"><Activity :size="24" /><span>衡读 · 智能抄表</span></div>
      <div class="login-story__copy">
        <span class="eyebrow eyebrow--light">METER OPERATIONS</span>
        <h1>让每一次读数<br><em>清晰可追溯</em></h1>
        <p>设备在线状态、抄表任务与视觉识别结果，在一个工作台内完成闭环。</p>
      </div>
      <div class="meter-orbit" aria-hidden="true"><i /><i /><i /><span>99.8<small>%</small></span></div>
      <div class="login-metric"><span>识别服务</span><strong>ONLINE</strong></div>
    </section>
    <section class="login-form-wrap">
      <form class="login-form" @submit.prevent="submit">
        <header><span class="eyebrow">WELCOME BACK</span><h2>登录控制台</h2><p>使用系统账号继续</p></header>
        <label><span>用户名</span><div class="input-shell"><UserRound :size="18" /><input v-model="username" maxlength="64" autocomplete="username" placeholder="请输入用户名" /></div></label>
        <label><span>密码</span><div class="input-shell"><LockKeyhole :size="18" /><input v-model="password" :type="visible ? 'text' : 'password'" maxlength="128" autocomplete="current-password" placeholder="请输入密码" /><button type="button" class="icon-button" @click="visible = !visible"><EyeOff v-if="visible" :size="18" /><Eye v-else :size="18" /></button></div></label>
        <button class="primary-button login-submit" :disabled="loading"><span v-if="loading" class="loader loader--light" />{{ loading ? '正在登录' : '进入系统' }}<ArrowRight v-if="!loading" :size="18" /></button>
        <p class="login-help">居民还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
      </form>
    </section>
  </main>
</template>
