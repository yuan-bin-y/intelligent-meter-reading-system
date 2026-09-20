package com.byy.meterreading.mapper.projection;

import lombok.Data;

import java.time.LocalDateTime;

/** 一个任务历次提交结果的审核历史投影。 */
@Data
public class MeterReadingReviewHistoryRow {
    private Long reviewId;
    private Long resultId;
    private Integer attemptNo;
    private String reviewAction;
    private Long reviewerId;
    private String reviewerName;
    private String reviewReason;
    private LocalDateTime reviewedAt;
}
