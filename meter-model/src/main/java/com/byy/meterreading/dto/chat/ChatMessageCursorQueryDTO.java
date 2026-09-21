package com.byy.meterreading.dto.chat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * 聊天消息游标查询参数。
 * beforeMessageId 为空时查询最新一页；不为空时查询该消息之前的历史消息。
 */
public record ChatMessageCursorQueryDTO(
        @Positive(message = "消息游标ID必须大于0")
        Long beforeMessageId,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize
) {

    public ChatMessageCursorQueryDTO {
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
