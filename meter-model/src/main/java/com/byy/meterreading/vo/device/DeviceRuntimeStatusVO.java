package com.byy.meterreading.vo.device;

/**
 * 设备当前实时在线状态。
 *
 * @param deviceId 设备数据库主键
 * @param deviceNo 设备编号
 * @param online   Redis 心跳是否仍然有效
 */
public record DeviceRuntimeStatusVO(
        Long deviceId,
        String deviceNo,
        boolean online
) {
}
