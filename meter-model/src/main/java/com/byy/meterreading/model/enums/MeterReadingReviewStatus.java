package com.byy.meterreading.model.enums;

/**
 * 抄表结果审核状态；当前任务执行模块只会创建 PENDING 结果。
 */
public enum MeterReadingReviewStatus {

    PENDING("待审核"),
    APPROVED("审核通过"),
    REJECTED("审核驳回");

    private final String description;

    MeterReadingReviewStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** 一次提交结果只允许从待审核进入一个终态。 */
    public boolean canTransitionTo(MeterReadingReviewStatus target) {
        return this == PENDING
                && (target == APPROVED || target == REJECTED);
    }
}
