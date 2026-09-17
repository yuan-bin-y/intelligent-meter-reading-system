package com.byy.meterreading.vo.residentmeter;

import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterType;

import java.time.LocalDateTime;

/**
 * 居民名下的一条表具信息。
 */
public record ResidentBoundMeterVO(
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
