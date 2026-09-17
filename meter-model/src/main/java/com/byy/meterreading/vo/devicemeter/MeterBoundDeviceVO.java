package com.byy.meterreading.vo.devicemeter;

import com.byy.meterreading.model.enums.DeviceType;

import java.time.LocalDateTime;

/**
 * 指定表具绑定的一条设备信息。
 */
public record MeterBoundDeviceVO(
        Long deviceId,
        String deviceNo,
        String deviceName,
        DeviceType deviceType,
        Integer status,
        Integer version,
        LocalDateTime boundAt
) {
}
