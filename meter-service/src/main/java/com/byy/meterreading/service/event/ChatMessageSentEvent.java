package com.byy.meterreading.service.event;

import com.byy.meterreading.vo.chat.ChatMessageVO;

import java.util.List;

/** 事务提交成功后需要推送给会话成员的聊天消息事件。 */
public record ChatMessageSentEvent(
        ChatMessageVO message,
        List<Long> recipientUserIds
) {

    public ChatMessageSentEvent {
        recipientUserIds = recipientUserIds == null
                ? List.of()
                : List.copyOf(recipientUserIds);
    }
}
