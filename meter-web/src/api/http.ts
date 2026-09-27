import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResult, LoginResult } from '../types'

const client = axios.create({ baseURL: '/', timeout: 20_000 })
const plainClient = axios.create({ baseURL: '/', timeout: 20_000 })

let refreshPromise: Promise<void> | null = null

export function getAccessToken() {
  return localStorage.getItem('meter.accessToken')
}

export function saveTokens(tokens: Pick<LoginResult, 'accessToken' | 'refreshToken'>) {
  localStorage.setItem('meter.accessToken', tokens.accessToken)
  localStorage.setItem('meter.refreshToken', tokens.refreshToken)
}

export function clearTokens() {
  localStorage.removeItem('meter.accessToken')
  localStorage.removeItem('meter.refreshToken')
}

client.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

async function refreshTokens() {
  const refreshToken = localStorage.getItem('meter.refreshToken')
  if (!refreshToken) throw new Error('登录状态已失效')
  const response = await plainClient.post<ApiResult<LoginResult>>('/api/v1/auth/refresh', { refreshToken })
  if (response.data.code !== 'OK') throw new Error(response.data.message)
  saveTokens(response.data.data)
}

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as Partial<ApiResult<unknown>> | undefined
    return body?.message || (error.code === 'ECONNABORTED' ? '请求超时' : '无法连接后端服务')
  }
  return error instanceof Error ? error.message : '请求失败'
}

export async function request<T>(config: AxiosRequestConfig, allowRefresh = true): Promise<T> {
  try {
    const response = await client.request<ApiResult<T>>(config)
    if (response.data.code !== 'OK') throw new Error(response.data.message || response.data.code)
    return response.data.data
  } catch (error) {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    if (allowRefresh && status === 401 && !String(config.url).includes('/auth/')) {
      try {
        refreshPromise ??= refreshTokens().finally(() => { refreshPromise = null })
        await refreshPromise
        return request<T>(config, false)
      } catch {
        clearTokens()
        window.location.href = '/login'
        throw new Error('登录状态已失效')
      }
    }
    throw new Error(errorMessage(error))
  }
}
