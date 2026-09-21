package com.byy.meterreading.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 居民为抄表任务创建沟通会话的请求参数。
 *
 * @param taskId 抄表任务 ID；居民和抄表员信息由后端根据任务关系确定
 */
public record CreateTaskConversationDTO(
        @NotNull(message = "抄表任务ID不能为空")
        @Positive(message = "抄表任务ID必须大于0")
        Long taskId
) {
}
