package com.byy.meterreading.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 管理员认领等待中的客服会话时提交的并发版本。 */
public record ClaimChatConversationDTO(
        @NotNull(message = "会话版本不能为空")
        @PositiveOrZero(message = "会话版本不能小于0")
        Integer version
) {
}
