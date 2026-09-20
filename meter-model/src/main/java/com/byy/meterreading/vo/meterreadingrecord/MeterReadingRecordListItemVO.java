package com.byy.meterreading.vo.meterreadingrecord;

import com.byy.meterreading.model.enums.TaskExecutorType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 正式抄表记录分页列表项。 */
public record MeterReadingRecordListItemVO(
        Long recordId,
        Long resultId,
        Long taskId,
        String taskNo,
        Long meterId,
        String meterNo,
        String meterName,
        String meterType,
        String unit,
        BigDecimal readingValue,
        LocalDateTime readingAt,
        TaskExecutorType sourceType,
        String sourceTypeName,
        Long executorId,
        String executorName,
        Long reviewerId,
        String reviewerName,
        LocalDateTime reviewedAt
) {
}
