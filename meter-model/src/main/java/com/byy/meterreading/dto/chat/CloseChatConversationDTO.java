package com.byy.meterreading.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 关闭聊天会话的请求参数。 */
public record CloseChatConversationDTO(
        @NotNull(message = "会话版本不能为空")
        @PositiveOrZero(message = "会话版本不能小于0")
        Integer version,

        @NotBlank(message = "关闭原因不能为空")
        @Size(max = 500, message = "关闭原因长度不能超过500个字符")
        String reason
) {

    public CloseChatConversationDTO {
        reason = reason == null ? null : reason.trim();
    }
}
