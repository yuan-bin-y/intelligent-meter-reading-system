package com.byy.meterreading.vo.chat;

import com.byy.meterreading.model.enums.ChatConversationStatus;
import com.byy.meterreading.model.enums.ChatConversationType;

import java.time.LocalDateTime;
import java.util.List;

/** 会话详情，包含关联任务、关闭信息和全部成员。 */
public record ChatConversationDetailVO(
        Long conversationId,
        ChatConversationType conversationType,
        String conversationTypeName,
        Long taskId,
        String taskNo,
        String meterNo,
        String meterName,
        String subject,
        ChatConversationStatus status,
        String statusName,
        Long createdBy,
        String creatorDisplayName,
        List<ChatMemberVO> members,
        Long closedBy,
        String closedByDisplayName,
        String closeReason,
        LocalDateTime closedAt,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public ChatConversationDetailVO {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
