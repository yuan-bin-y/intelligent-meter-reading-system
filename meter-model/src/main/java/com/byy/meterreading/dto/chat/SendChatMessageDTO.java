package com.byy.meterreading.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** WebSocket 发送聊天消息的请求参数。 */
public record SendChatMessageDTO(
        @NotNull(message = "会话ID不能为空")
        @Positive(message = "会话ID必须大于0")
        Long conversationId,

        @NotBlank(message = "客户端消息ID不能为空")
        @Size(max = 64, message = "客户端消息ID长度不能超过64个字符")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "客户端消息ID只能包含字母、数字、下划线和短横线")
        String clientMessageId,

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容长度不能超过2000个字符")
        String content
) {

    public SendChatMessageDTO {
        clientMessageId = clientMessageId == null ? null : clientMessageId.trim();
        content = content == null ? null : content.trim();
    }
}
