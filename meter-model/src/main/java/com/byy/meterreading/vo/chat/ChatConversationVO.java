package com.byy.meterreading.vo.chat;

import com.byy.meterreading.model.enums.ChatConversationStatus;
import com.byy.meterreading.model.enums.ChatConversationType;

import java.time.LocalDateTime;

/** 创建、认领、转交或关闭会话后返回的最新会话状态。 */
public record ChatConversationVO(
        Long conversationId,
        ChatConversationType conversationType,
        String conversationTypeName,
        Long taskId,
        String subject,
        ChatConversationStatus status,
        String statusName,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
