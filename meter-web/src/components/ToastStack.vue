<script setup lang="ts">
import { CircleCheck, CircleX, Info, X } from 'lucide-vue-next'
import { removeToast, toasts } from '../stores/toast'
</script>

<template>
  <div class="toast-stack" aria-live="polite">
    <div v-for="item in toasts" :key="item.id" class="toast" :class="`toast--${item.kind}`">
      <CircleCheck v-if="item.kind === 'success'" :size="18" />
      <CircleX v-else-if="item.kind === 'error'" :size="18" />
      <Info v-else :size="18" />
      <span>{{ item.message }}</span>
      <button class="icon-button" aria-label="关闭" @click="removeToast(item.id)"><X :size="15" /></button>
    </div>
  </div>
</template>
