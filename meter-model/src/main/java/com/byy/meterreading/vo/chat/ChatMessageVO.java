package com.byy.meterreading.vo.chat;

import com.byy.meterreading.model.enums.ChatMessageSenderRole;
import com.byy.meterreading.model.enums.ChatMessageType;

import java.time.LocalDateTime;

/** 一条持久化聊天消息。 */
public record ChatMessageVO(
        Long messageId,
        Long conversationId,
        String clientMessageId,
        Long senderId,
        String senderDisplayName,
        ChatMessageSenderRole senderRole,
        String senderRoleName,
        ChatMessageType messageType,
        String messageTypeName,
        String content,
        boolean deleted,
        LocalDateTime createdAt
) {
}
