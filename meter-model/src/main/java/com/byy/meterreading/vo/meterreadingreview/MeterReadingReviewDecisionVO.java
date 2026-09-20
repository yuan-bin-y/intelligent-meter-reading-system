package com.byy.meterreading.vo.meterreadingreview;

import com.byy.meterreading.model.enums.MeterReadingReviewStatus;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;

import java.time.LocalDateTime;

/** 审核通过或驳回后的最新状态与版本。 */
public record MeterReadingReviewDecisionVO(
        Long resultId,
        MeterReadingReviewStatus reviewStatus,
        Integer resultVersion,
        Long taskId,
        MeterReadingTaskStatus taskStatus,
        Integer taskVersion,
        Long recordId,
        LocalDateTime reviewedAt
) {
}
