package com.byy.meterreading.vo.chat;

import com.byy.meterreading.model.enums.ChatMemberRole;

import java.time.LocalDateTime;

/** 会话成员信息及其读取进度。 */
public record ChatMemberVO(
        Long userId,
        String username,
        String displayName,
        ChatMemberRole memberRole,
        String memberRoleName,
        Long lastReadMessageId,
        LocalDateTime lastReadAt,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        boolean active
) {
}
