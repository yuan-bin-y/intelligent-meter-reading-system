package com.byy.meterreading.service;

import com.byy.meterreading.dto.chat.SendChatMessageDTO;
import com.byy.meterreading.dto.chat.WebSocketReadChatMessageDTO;
import com.byy.meterreading.vo.chat.ChatMessageVO;
import com.byy.meterreading.vo.chat.ChatReadReceiptVO;

/** WebSocket 实时消息发送和已读回执业务。 */
public interface ChatMessageService {

    /**
     * 保存当前用户发送的聊天消息。
     * 消息提交成功后，由实现层安排向有效会话成员实时推送。
     */
    ChatMessageVO sendMessage(
            Long senderId,
            SendChatMessageDTO sendDTO
    );

    /**
     * 推进当前用户在指定会话中的已读游标。
     * 更新提交成功后，由实现层安排向有效会话成员推送已读回执。
     */
    ChatReadReceiptVO markRead(
            Long currentUserId,
            WebSocketReadChatMessageDTO readDTO
    );
}
