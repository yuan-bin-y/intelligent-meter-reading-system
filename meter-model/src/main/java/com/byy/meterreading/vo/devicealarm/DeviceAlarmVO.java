package com.byy.meterreading.vo.devicealarm;

import com.byy.meterreading.model.enums.DeviceAlarmStatus;
import com.byy.meterreading.model.enums.DeviceAlarmType;

import java.time.LocalDateTime;

/**
 * 设备告警响应，同时包含告警记录和关联设备的基本信息。
 */
public record DeviceAlarmVO(
        Long id,
        Long deviceId,
        String deviceNo,
        String deviceName,
        DeviceAlarmType alarmType,
        String alarmTypeName,
        DeviceAlarmStatus alarmStatus,
        String alarmStatusName,
        LocalDateTime occurredAt,
        LocalDateTime recoveredAt
) {
}
