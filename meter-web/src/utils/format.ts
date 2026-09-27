export function cleanParams(value: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== '' && item !== null && item !== undefined))
}

export function formatDate(value: unknown) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

export const meterTypes: Record<string, string> = { WATER: '水表', ELECTRIC: '电表', GAS: '燃气表' }
export const displayTypes: Record<string, string> = { LCD: '液晶显示', MECHANICAL_ROLLER: '机械滚轮' }
export const deviceTypes: Record<string, string> = { CAMERA: '摄像头', GATEWAY: '网关', EDGE_DEVICE: '边缘设备' }
export const meterStatuses: Record<number, string> = { 0: '停用', 1: '正常', 2: '维护中', 3: '已报废' }
export const userStatuses: Record<number, string> = { 0: '禁用', 1: '启用' }
