package com.byy.meterreading.vo.chat;

/** 当前登录用户在全部有效会话中的未读消息统计。 */
public record ChatUnreadCountVO(
        long unreadMessageCount,
        long unreadConversationCount
) {
}
