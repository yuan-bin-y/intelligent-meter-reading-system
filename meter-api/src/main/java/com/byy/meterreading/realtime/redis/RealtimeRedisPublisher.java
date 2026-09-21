package com.byy.meterreading.realtime.redis;

import com.byy.meterreading.realtime.config.RealtimeProperties;
import com.byy.meterreading.realtime.registry.SseConnectionRegistry;
import com.byy.meterreading.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 发布跨实例通知；Redis 不可用时回退到当前实例的 SSE 连接。 */
@Component
public class RealtimeRedisPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            RealtimeRedisPublisher.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeProperties properties;
    private final NotificationService notificationService;
    private final SseConnectionRegistry registry;

    public RealtimeRedisPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RealtimeProperties properties,
            NotificationService notificationService,
            SseConnectionRegistry registry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.notificationService = notificationService;
        this.registry = registry;
    }

    public void publish(Long notificationId, Long recipientUserId) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new RealtimeRedisMessage(
                            notificationId,
                            recipientUserId
                    )
            );
            Long subscribers = redisTemplate.convertAndSend(
                    properties.redisChannel(),
                    payload
            );
            if (subscribers == null || subscribers == 0L) {
                deliverLocally(notificationId, recipientUserId);
            }
        } catch (DataAccessException exception) {
            LOGGER.warn("Redis实时通知发布失败，已回退到本机SSE");
            deliverLocally(notificationId, recipientUserId);
        } catch (Exception exception) {
            throw new IllegalStateException("实时通知序列化失败", exception);
        }
    }

    private void deliverLocally(
            Long notificationId,
            Long recipientUserId
    ) {
        registry.sendToUser(
                recipientUserId,
                notificationService.getForDelivery(
                        notificationId,
                        recipientUserId
                )
        );
    }
}
