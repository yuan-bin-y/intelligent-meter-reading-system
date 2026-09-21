package com.byy.meterreading.websocket;

import com.byy.meterreading.service.event.ChatMessageSentEvent;
import com.byy.meterreading.service.event.ChatReadReceiptEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 数据库事务提交成功后的聊天 WebSocket 推送器。
 *
 * <p>消息持久化和实时推送分开执行，保证数据库回滚时客户端不会提前收到
 * 一条实际不存在的消息。离线用户未收到实时帧时，可通过 REST 历史消息和
 * 未读统计接口恢复数据。</p>
 */
@Component
public class ChatWebSocketEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ChatWebSocketEventListener.class
    );

    private static final String CHAT_DESTINATION = "/queue/chat";
    private static final String READ_DESTINATION = "/queue/chat.read";

    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketEventListener(
            SimpMessagingTemplate messagingTemplate
    ) {
        this.messagingTemplate = messagingTemplate;
    }

    /** 将已经成功落库的聊天消息推送到每个有效成员的全部在线连接。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        for (Long userId : event.recipientUserIds()) {
            sendToUser(
                    userId,
                    CHAT_DESTINATION,
                    event.message(),
                    event.message().conversationId()
            );
        }
    }

    /** 将已经成功更新的已读游标推送给会话中的全部有效成员。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadReceipt(ChatReadReceiptEvent event) {
        for (Long userId : event.recipientUserIds()) {
            sendToUser(
                    userId,
                    READ_DESTINATION,
                    event.receipt(),
                    event.receipt().conversationId()
            );
        }
    }

    /**
     * 单个用户推送失败只记录日志，不影响其他成员，也不会反向影响已提交事务。
     */
    private void sendToUser(
            Long userId,
            String destination,
            Object payload,
            Long conversationId
    ) {
        if (userId == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    destination,
                    payload
            );
        } catch (MessagingException exception) {
            LOGGER.warn(
                    "聊天 WebSocket 推送失败，userId={}, conversationId={}, destination={}",
                    userId,
                    conversationId,
                    destination,
                    exception
            );
        }
    }
}
