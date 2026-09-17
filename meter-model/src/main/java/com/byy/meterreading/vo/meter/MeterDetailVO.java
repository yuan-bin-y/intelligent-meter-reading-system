package com.byy.meterreading.vo.meter;

import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表具完整档案响应。
 */
public record MeterDetailVO(
        Long meterId,
        String meterNo,
        String meterName,
        MeterType meterType,
        MeterDisplayType displayType,
        String unit,
        Integer integerDigits,
        Integer decimalDigits,
        BigDecimal initialReading,
        LocalDate installedAt,
        Integer status,
        Integer version,
        String remark,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
