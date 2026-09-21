package com.byy.meterreading.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 管理员将客服会话转交给另一名管理员的请求参数。 */
public record TransferChatConversationDTO(
        @NotNull(message = "目标管理员ID不能为空")
        @Positive(message = "目标管理员ID必须大于0")
        Long targetAdminId,

        @NotNull(message = "会话版本不能为空")
        @PositiveOrZero(message = "会话版本不能小于0")
        Integer version,

        @NotBlank(message = "转交原因不能为空")
        @Size(max = 500, message = "转交原因长度不能超过500个字符")
        String reason
) {

    public TransferChatConversationDTO {
        reason = reason == null ? null : reason.trim();
    }
}
