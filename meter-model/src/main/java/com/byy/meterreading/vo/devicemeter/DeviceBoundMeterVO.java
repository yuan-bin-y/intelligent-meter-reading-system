package com.byy.meterreading.vo.devicemeter;

import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterType;

import java.time.LocalDateTime;

/**
 * 指定设备绑定的一条表具信息。
 */
public record DeviceBoundMeterVO(
        Long meterId,
        String meterNo,
        String meterName,
        MeterType meterType,
        MeterDisplayType displayType,
        String unit,
        Integer status,
        Integer version,
        LocalDateTime boundAt
) {
}
