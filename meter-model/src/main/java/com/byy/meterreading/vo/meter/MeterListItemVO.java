package com.byy.meterreading.vo.meter;

import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterType;

import java.time.LocalDateTime;

/**
 * 表具分页列表中的一条记录。
 */
public record MeterListItemVO(
        Long meterId,
        String meterNo,
        String meterName,
        MeterType meterType,
        MeterDisplayType displayType,
        String unit,
        Integer status,
        Integer version,
        LocalDateTime updatedAt
) {
}
