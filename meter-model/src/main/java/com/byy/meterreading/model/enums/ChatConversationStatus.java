package com.byy.meterreading.model.enums;

/** 聊天会话状态及其允许的状态流转。 */
public enum ChatConversationStatus {

    WAITING("等待管理员认领"),
    ACTIVE("沟通中"),
    CLOSED("已关闭");

    private final String description;

    ChatConversationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 管理员客服会话由等待认领进入沟通中；任何未关闭会话都允许关闭。
     * 任务沟通会话创建时直接处于 ACTIVE，不经过 WAITING。
     */
    public boolean canTransitionTo(ChatConversationStatus target) {
        if (target == null || this == target) {
            return false;
        }
        return switch (this) {
            case WAITING -> target == ACTIVE || target == CLOSED;
            case ACTIVE -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
