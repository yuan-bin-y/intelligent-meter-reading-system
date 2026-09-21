package com.byy.meterreading.vo.chat;

import java.util.List;

/**
 * 基于消息主键游标的历史消息查询结果。
 * nextBeforeMessageId 传给下一次请求即可继续向前加载历史消息。
 */
public record ChatMessagePageVO(
        List<ChatMessageVO> records,
        Long nextBeforeMessageId,
        boolean hasMore
) {

    public ChatMessagePageVO {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
