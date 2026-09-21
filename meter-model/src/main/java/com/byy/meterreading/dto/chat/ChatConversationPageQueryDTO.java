package com.byy.meterreading.dto.chat;

import com.byy.meterreading.model.enums.ChatConversationStatus;
import com.byy.meterreading.model.enums.ChatConversationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 聊天会话分页查询参数。 */
public record ChatConversationPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize,

        ChatConversationType type,

        ChatConversationStatus status
) {

    public ChatConversationPageQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
