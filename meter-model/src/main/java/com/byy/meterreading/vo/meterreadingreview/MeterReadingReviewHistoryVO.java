package com.byy.meterreading.vo.meterreadingreview;

import com.byy.meterreading.model.enums.MeterReadingReviewStatus;

import java.time.LocalDateTime;

/** 一次提交结果的审核历史。 */
public record MeterReadingReviewHistoryVO(
        Long reviewId,
        Long resultId,
        Integer attemptNo,
        MeterReadingReviewStatus reviewAction,
        String reviewActionName,
        Long reviewerId,
        String reviewerName,
        String reviewReason,
        LocalDateTime reviewedAt
) {
}
