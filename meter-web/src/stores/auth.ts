import { reactive } from 'vue'
import { clearTokens, getAccessToken, request, saveTokens } from '../api/http'
import type { CurrentUser, LoginResult } from '../types'

export const authState = reactive<{
  user: CurrentUser | null
  loading: boolean
}>({
  user: null,
  loading: false,
})

export async function login(username: string, password: string) {
  const result = await request<LoginResult>({
    url: '/api/v1/auth/login',
    method: 'POST',
    data: { username, password },
  }, false)
  saveTokens(result)
  authState.user = result
  return result
}

export async function loadCurrentUser() {
  if (!getAccessToken()) return null
  authState.loading = true
  try {
    authState.user = await request<CurrentUser>({ url: '/api/v1/auth/me' })
    return authState.user
  } finally {
    authState.loading = false
  }
}

export async function logout() {
  try {
    if (getAccessToken()) await request<void>({ url: '/api/v1/auth/logout', method: 'POST' }, false)
  } finally {
    authState.user = null
    clearTokens()
  }
}

export function isAdmin() {
  return authState.user?.roles.includes('ADMIN') ?? false
}
