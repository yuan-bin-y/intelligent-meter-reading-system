package com.byy.meterreading.model.enums;

/** 通知点击后对应的业务资源类型。 */
public enum NotificationResourceType {

    AI_RECOGNITION_TASK("AI识别任务"),
    DEVICE("设备"),
    METER_READING_TASK("抄表任务"),
    METER_READING_RESULT("抄表结果"),
    CHAT_CONVERSATION("聊天会话");

    private final String description;

    NotificationResourceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
