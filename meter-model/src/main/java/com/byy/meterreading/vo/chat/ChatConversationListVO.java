package com.byy.meterreading.vo.chat;

import com.byy.meterreading.model.enums.ChatConversationStatus;
import com.byy.meterreading.model.enums.ChatConversationType;
import com.byy.meterreading.model.enums.ChatMessageSenderRole;

import java.time.LocalDateTime;

/** 会话分页列表中的单条摘要。 */
public record ChatConversationListVO(
        Long conversationId,
        ChatConversationType conversationType,
        String conversationTypeName,
        Long taskId,
        String taskNo,
        String subject,
        ChatConversationStatus status,
        String statusName,
        Long lastMessageId,
        String lastMessageContent,
        Long lastMessageSenderId,
        ChatMessageSenderRole lastMessageSenderRole,
        LocalDateTime lastMessageAt,
        long unreadCount,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
