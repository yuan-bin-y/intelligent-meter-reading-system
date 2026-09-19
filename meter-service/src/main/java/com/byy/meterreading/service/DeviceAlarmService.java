package com.byy.meterreading.service;

/**
 * 设备离线检测与告警状态处理业务。
 */
public interface DeviceAlarmService {

    /**
     * 扫描启用设备，根据 Redis 心跳创建离线告警或恢复已有告警。
     */
    void detectOfflineDevices();
}
