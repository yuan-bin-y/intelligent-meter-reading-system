package com.byy.meterreading.realtime.listener;

import com.byy.meterreading.service.BusinessNotificationService;
import com.byy.meterreading.service.event.ChatMessageSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 聊天消息事务提交后，为未在线成员保留持久化通知。 */
@Component
public class ChatNotificationEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ChatNotificationEventListener.class
    );

    private final BusinessNotificationService notificationService;

    public ChatNotificationEventListener(
            BusinessNotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        try {
            notificationService.notifyChatMessage(
                    event.message(),
                    event.recipientUserIds()
            );
        } catch (RuntimeException exception) {
            // 聊天消息已经提交，通知失败不能让客户端误以为消息发送失败。
            LOGGER.warn(
                    "聊天通知创建失败，messageId={}",
                    event.message().messageId(),
                    exception
            );
        }
    }
}
