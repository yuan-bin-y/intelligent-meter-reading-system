package com.byy.meterreading.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 将会话标记为已读时提交的消息位置。 */
public record ReadChatMessageDTO(
        @NotNull(message = "最后已读消息ID不能为空")
        @Positive(message = "最后已读消息ID必须大于0")
        Long lastReadMessageId
) {
}
