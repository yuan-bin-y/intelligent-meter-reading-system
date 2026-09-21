package com.byy.meterreading.model.enums;

/** 会话成员在聊天业务中的角色。 */
public enum ChatMemberRole {

    RESIDENT("居民"),
    METER_READER("抄表员"),
    ADMIN("管理员");

    private final String description;

    ChatMemberRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
