package com.byy.meterreading.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * WebSocket 推进会话已读位置的请求参数。
 *
 * <p>REST 接口从 URL 路径获取 conversationId；WebSocket 消息没有路径参数，
 * 因此需要在 STOMP 消息体中同时携带会话 ID 和最后已读消息 ID。</p>
 */
public record WebSocketReadChatMessageDTO(
        @NotNull(message = "会话ID不能为空")
        @Positive(message = "会话ID必须大于0")
        Long conversationId,

        @NotNull(message = "最后已读消息ID不能为空")
        @Positive(message = "最后已读消息ID必须大于0")
        Long lastReadMessageId
) {
}
