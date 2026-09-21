package com.byy.meterreading.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 居民创建管理员客服会话的请求参数。
 * 创建会话和保存首条消息将在同一个事务中完成。
 */
public record CreateAdminSupportConversationDTO(
        @NotBlank(message = "会话主题不能为空")
        @Size(max = 128, message = "会话主题长度不能超过128个字符")
        String subject,

        @NotBlank(message = "客户端消息ID不能为空")
        @Size(max = 64, message = "客户端消息ID长度不能超过64个字符")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "客户端消息ID只能包含字母、数字、下划线和短横线")
        String clientMessageId,

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容长度不能超过2000个字符")
        String content
) {

    public CreateAdminSupportConversationDTO {
        subject = trim(subject);
        clientMessageId = trim(clientMessageId);
        content = trim(content);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
