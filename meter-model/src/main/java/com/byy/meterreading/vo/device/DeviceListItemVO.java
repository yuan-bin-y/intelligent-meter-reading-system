package com.byy.meterreading.vo.device;

import com.byy.meterreading.model.enums.DeviceType;

import java.time.LocalDateTime;

/**
 * 设备分页列表中的一条记录。
 */
public record DeviceListItemVO(
        Long deviceId,
        String deviceNo,
        String deviceName,
        DeviceType deviceType,
        Integer status,
        Integer version,
        LocalDateTime updatedAt
) {
}
