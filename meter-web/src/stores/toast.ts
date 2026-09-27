import { reactive } from 'vue'

export type ToastKind = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}

export const toasts = reactive<ToastItem[]>([])

export function notify(message: string, kind: ToastKind = 'info') {
  const id = Date.now() + Math.floor(Math.random() * 1000)
  toasts.push({ id, kind, message })
  window.setTimeout(() => removeToast(id), 3200)
}

export function removeToast(id: number) {
  const index = toasts.findIndex((item) => item.id === id)
  if (index >= 0) toasts.splice(index, 1)
}
