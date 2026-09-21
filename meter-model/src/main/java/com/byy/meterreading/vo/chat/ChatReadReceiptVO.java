package com.byy.meterreading.vo.chat;

import java.time.LocalDateTime;

/** 更新读取进度后向会话参与者推送的已读回执。 */
public record ChatReadReceiptVO(
        Long conversationId,
        Long userId,
        Long lastReadMessageId,
        LocalDateTime lastReadAt
) {
}
