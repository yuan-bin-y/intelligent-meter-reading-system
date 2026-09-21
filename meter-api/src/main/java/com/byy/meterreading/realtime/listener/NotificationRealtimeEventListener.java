package com.byy.meterreading.realtime.listener;

import com.byy.meterreading.realtime.redis.RealtimeRedisPublisher;
import com.byy.meterreading.service.event.NotificationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 数据库事务提交后才投递 SSE；推送失败不会破坏已提交业务。 */
@Component
public class NotificationRealtimeEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            NotificationRealtimeEventListener.class
    );

    private final RealtimeRedisPublisher redisPublisher;

    public NotificationRealtimeEventListener(
            RealtimeRedisPublisher redisPublisher
    ) {
        this.redisPublisher = redisPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(NotificationCreatedEvent event) {
        try {
            redisPublisher.publish(
                    event.notificationId(),
                    event.recipientUserId()
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "实时通知投递失败，notificationId={}",
                    event.notificationId(),
                    exception
            );
        }
    }
}
