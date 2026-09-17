package com.byy.meterreading.vo.residentmeter;

import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterType;

import java.time.LocalDateTime;

/**
 * 管理员查看的完整居民表具绑定关系。
 */
public record ResidentMeterBindingVO(
        Long residentId,
        String username,
        String displayName,
        Integer residentStatus,
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
