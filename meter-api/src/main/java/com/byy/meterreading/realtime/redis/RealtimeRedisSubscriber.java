package com.byy.meterreading.realtime.redis;

import com.byy.meterreading.realtime.registry.SseConnectionRegistry;
import com.byy.meterreading.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** 每个应用实例订阅同一频道，只向本实例持有的 SSE 连接发送。 */
@Component
public class RealtimeRedisSubscriber implements MessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            RealtimeRedisSubscriber.class
    );

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final SseConnectionRegistry registry;

    public RealtimeRedisSubscriber(
            ObjectMapper objectMapper,
            NotificationService notificationService,
            SseConnectionRegistry registry
    ) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.registry = registry;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RealtimeRedisMessage payload = objectMapper.readValue(
                    new String(
                            message.getBody(),
                            StandardCharsets.UTF_8
                    ),
                    RealtimeRedisMessage.class
            );
            registry.sendToUser(
                    payload.recipientUserId(),
                    notificationService.getForDelivery(
                            payload.notificationId(),
                            payload.recipientUserId()
                    )
            );
        } catch (Exception exception) {
            // Pub/Sub 没有 ACK，失败通知仍可由列表和 Last-Event-ID 补偿。
            LOGGER.warn("已忽略无法投递的Redis实时通知", exception);
        }
    }
}
