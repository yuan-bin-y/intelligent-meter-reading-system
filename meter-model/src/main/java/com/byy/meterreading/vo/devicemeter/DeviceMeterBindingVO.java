package com.byy.meterreading.vo.devicemeter;

import com.byy.meterreading.model.enums.DeviceType;
import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterType;

import java.time.LocalDateTime;

/**
 * 管理员查看的完整设备表具绑定关系。
 */
public record DeviceMeterBindingVO(
        Long deviceId,
        String deviceNo,
        String deviceName,
        DeviceType deviceType,
        Integer deviceStatus,
        Long meterId,
        String meterNo,
        String meterName,
        MeterType meterType,
        MeterDisplayType displayType,
        String unit,
        Integer meterStatus,
        Long createdBy,
        LocalDateTime boundAt
) {
}
