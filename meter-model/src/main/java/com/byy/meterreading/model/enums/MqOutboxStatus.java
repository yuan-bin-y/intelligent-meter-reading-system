package com.byy.meterreading.model.enums;

/**
 * RabbitMQ Outbox 消息发送状态。
 *
 * <p>该状态只描述消息从数据库发送到 RabbitMQ 的过程，
 * SENT 表示 RabbitMQ 已确认接收消息，不表示 AI 已经完成识别。</p>
 */
public enum MqOutboxStatus {

    PENDING("待发送"),
    SENDING("发送中"),
    SENT("发送成功"),
    FAILED("发送失败");

    private final String description;

    MqOutboxStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断当前消息状态是否允许流转到目标状态。
     *
     * <p>发送过程异常或实例锁超时后可以回到 PENDING 等待重试；
     * 超过自动重试次数进入 FAILED，人工重试时也可以重新回到 PENDING。</p>
     */
    public boolean canTransitionTo(MqOutboxStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == SENDING;
            case SENDING -> target == SENT
                    || target == PENDING
                    || target == FAILED;
            case FAILED -> target == PENDING;
            case SENT -> false;
        };
    }
}
