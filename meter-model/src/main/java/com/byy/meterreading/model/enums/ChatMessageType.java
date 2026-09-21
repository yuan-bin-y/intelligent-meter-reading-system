package com.byy.meterreading.model.enums;

/** 聊天消息类型。 */
public enum ChatMessageType {

    TEXT("文本消息"),
    SYSTEM("系统消息");

    private final String description;

    ChatMessageType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
