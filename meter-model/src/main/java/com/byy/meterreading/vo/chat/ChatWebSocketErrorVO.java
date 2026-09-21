package com.byy.meterreading.vo.chat;

import java.time.LocalDateTime;

/** WebSocket 消息处理失败后发送给当前用户的结构化错误。 */
public record ChatWebSocketErrorVO(
        String code,
        String message,
        LocalDateTime occurredAt
) {
}
