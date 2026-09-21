package com.byy.meterreading.model.enums;

/**
 * AI 图片识别任务生命周期状态。
 *
 * <p>该状态只描述图片识别业务是否开始、成功或失败，
 * 不代表对应的 RabbitMQ 消息是否已经发送成功。</p>
 */
public enum AiRecognitionStatus {

    PENDING("待识别"),
    PROCESSING("识别中"),
    SUCCEEDED("识别成功"),
    FAILED("识别失败"),
    CANCELLED("已取消");

    private final String description;

    AiRecognitionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断当前识别状态是否允许流转到目标状态。
     *
     * <p>失败任务重新执行时回到待识别状态；识别成功和已取消均为终态。</p>
     */
    public boolean canTransitionTo(AiRecognitionStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == PROCESSING
                    || target == CANCELLED;
            case PROCESSING -> target == SUCCEEDED
                    || target == FAILED
                    || target == CANCELLED;
            case FAILED -> target == PENDING;
            case SUCCEEDED, CANCELLED -> false;
        };
    }
}
