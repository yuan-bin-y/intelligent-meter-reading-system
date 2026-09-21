package com.byy.meterreading.realtime.service;

import com.byy.meterreading.realtime.config.RealtimeProperties;
import com.byy.meterreading.realtime.registry.SseConnectionRegistry;
import com.byy.meterreading.service.NotificationService;
import com.byy.meterreading.vo.notification.NotificationVO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 建立 SSE 连接，并根据 Last-Event-ID 补偿断线期间的通知。 */
@Service
public class RealtimeNotificationService {

    private static final String INVALID_LAST_EVENT_ID =
            "Last-Event-ID必须是正整数";

    private final SseConnectionRegistry registry;
    private final NotificationService notificationService;
    private final RealtimeProperties properties;

    public RealtimeNotificationService(
            SseConnectionRegistry registry,
            NotificationService notificationService,
            RealtimeProperties properties
    ) {
        this.registry = registry;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    public SseEmitter subscribe(Long userId, String lastEventId) {
        Long afterId = parseLastEventId(lastEventId);
        SseEmitter emitter = registry.register(userId);
        if (afterId == null) {
            return emitter;
        }

        List<NotificationVO> missed = notificationService.replayAfter(
                userId,
                afterId,
                properties.replayLimit()
        );
        for (NotificationVO notification : missed) {
            if (!registry.sendReplay(userId, emitter, notification)) {
                break;
            }
        }
        return emitter;
    }

    private Long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(lastEventId.trim());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(INVALID_LAST_EVENT_ID);
        }
    }
}
