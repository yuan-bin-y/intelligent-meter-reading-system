package com.byy.meterreading.model.enums;

/** 系统通知类型。 */
public enum NotificationType {

    AI_RECOGNITION_SUCCEEDED("AI识别成功"),
    AI_RECOGNITION_FAILED("AI识别失败"),
    DEVICE_OFFLINE("设备离线"),
    DEVICE_RECOVERED("设备恢复在线"),
    TASK_ASSIGNED("抄表任务已分配"),
    TASK_STATUS_CHANGED("抄表任务状态变化"),
    REVIEW_APPROVED("抄表结果审核通过"),
    REVIEW_REJECTED("抄表结果审核驳回"),
    CHAT_MESSAGE("新的聊天消息");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
