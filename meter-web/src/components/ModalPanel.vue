<script setup lang="ts">
import { X } from 'lucide-vue-next'
defineProps<{ open: boolean; title: string; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="modal-backdrop" @mousedown.self="emit('close')">
        <section class="modal-panel" :class="{ 'modal-panel--wide': wide }" role="dialog" aria-modal="true">
          <header><h2>{{ title }}</h2><button class="icon-button" @click="emit('close')"><X :size="19" /></button></header>
          <div class="modal-panel__body"><slot /></div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
