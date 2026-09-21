package com.byy.meterreading.service.event;

import com.byy.meterreading.vo.chat.ChatReadReceiptVO;

import java.util.List;

/** 事务提交成功后需要推送给会话成员的已读回执事件。 */
public record ChatReadReceiptEvent(
        ChatReadReceiptVO receipt,
        List<Long> recipientUserIds
) {

    public ChatReadReceiptEvent {
        recipientUserIds = recipientUserIds == null
                ? List.of()
                : List.copyOf(recipientUserIds);
    }
}
