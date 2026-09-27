export interface ApiResult<T> {
  code: string
  message: string
  data: T
  traceId: string
}

export interface PageData<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

export interface CurrentUser {
  userId: number
  username: string
  displayName: string
  roles: string[]
}

export interface LoginResult extends CurrentUser {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn: number
}

export type RowData = Record<string, any>
