package com.byy.meterreading.model.enums;

/** 消息发送方角色；SYSTEM 只用于后端产生的系统消息。 */
public enum ChatMessageSenderRole {

    RESIDENT("居民"),
    METER_READER("抄表员"),
    ADMIN("管理员"),
    SYSTEM("系统");

    private final String description;

    ChatMessageSenderRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** 普通用户消息的发送角色与会话成员角色名称保持一致。 */
    public static ChatMessageSenderRole fromMemberRole(ChatMemberRole role) {
        if (role == null) {
            throw new IllegalArgumentException("会话成员角色不能为空");
        }
        return valueOf(role.name());
    }
}
