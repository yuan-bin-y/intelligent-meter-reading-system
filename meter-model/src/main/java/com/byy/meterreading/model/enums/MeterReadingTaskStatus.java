package com.byy.meterreading.model.enums;

/**
 * 抄表任务生命周期状态。
 */
public enum MeterReadingTaskStatus {

    PENDING("待执行"),
    PROCESSING("执行中"),
    PENDING_REVIEW("待审核"),
    COMPLETED("已完成"),
    FAILED("执行失败"),
    CANCELLED("已取消");

    private final String description;

    MeterReadingTaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断当前状态是否允许流转到目标状态。
     *
     * <p>Service 在执行具体业务操作前调用该方法，数据库更新时还需要
     * 同时携带当前状态和 version，防止并发请求绕过状态判断。</p>
     */
    public boolean canTransitionTo(MeterReadingTaskStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == PROCESSING
                    || target == CANCELLED;
            case PROCESSING -> target == PENDING_REVIEW
                    || target == FAILED
                    || target == CANCELLED;
            case PENDING_REVIEW -> target == COMPLETED
                    || target == PROCESSING
                    || target == FAILED;
            case FAILED -> target == PENDING
                    || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
