package com.byy.meterreading.vo.meterreadingreview;

import com.byy.meterreading.model.enums.MeterReadingReviewStatus;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.TaskExecutorType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 审核结果分页列表项。 */
public record MeterReadingResultListItemVO(
        Long resultId,
        Long taskId,
        String taskNo,
        Integer attemptNo,
        Long meterId,
        String meterNo,
        String meterName,
        BigDecimal readingValue,
        TaskExecutorType sourceType,
        String sourceTypeName,
        Long executorId,
        String executorName,
        MeterReadingReviewStatus reviewStatus,
        String reviewStatusName,
        MeterReadingTaskStatus taskStatus,
        String taskStatusName,
        Integer resultVersion,
        Integer taskVersion,
        LocalDateTime submittedAt
) {
}
