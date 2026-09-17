package com.byy.meterreading.vo.device;

import com.byy.meterreading.model.enums.DeviceType;

import java.time.LocalDateTime;

/**
 * 设备完整档案响应。
 */
public record DeviceDetailVO(
        Long deviceId,
        String deviceNo,
        String deviceName,
        DeviceType deviceType,
        Integer status,
        Integer version,
        String remark,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
