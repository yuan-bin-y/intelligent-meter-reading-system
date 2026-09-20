package com.byy.meterreading.vo.meterreadingtask;

import com.byy.meterreading.model.enums.MeterReadingReviewStatus;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人工或设备成功提交抄表结果后的响应。
 */
public record MeterReadingSubmissionVO(
        Long resultId,
        Long taskId,
        BigDecimal readingValue,
        MeterReadingReviewStatus reviewStatus,
        MeterReadingTaskStatus taskStatus,
        Integer taskVersion,
        LocalDateTime submittedAt
) {
}
