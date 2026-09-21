package com.byy.meterreading.model.enums;

/** 聊天会话对应的业务类型。 */
public enum ChatConversationType {

    TASK_SERVICE("居民与抄表员的任务沟通"),
    ADMIN_SUPPORT("居民与管理员的客服沟通");

    private final String description;

    ChatConversationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
