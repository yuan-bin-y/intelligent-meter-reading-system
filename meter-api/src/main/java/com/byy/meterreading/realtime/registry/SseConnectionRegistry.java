package com.byy.meterreading.realtime.registry;

import com.byy.meterreading.realtime.config.RealtimeProperties;
import com.byy.meterreading.vo.notification.NotificationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理当前应用实例中的 SSE 连接。
 * 一个用户可以同时打开多个页面，因此每个用户保存一组连接。
 */
@Component
public class SseConnectionRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SseConnectionRegistry.class
    );

    private final RealtimeProperties properties;
    private final ConcurrentHashMap<Long, Set<SseEmitter>> connections =
            new ConcurrentHashMap<>();

    public SseConnectionRegistry(RealtimeProperties properties) {
        this.properties = properties;
    }

    /** 注册连接并立即发送连接确认及浏览器重连间隔。 */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(
                properties.timeout().toMillis()
        );
        connections.computeIfAbsent(
                userId,
                ignored -> ConcurrentHashMap.newKeySet()
        ).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .reconnectTime(3000L)
                    .data("connected"));
        } catch (IOException | RuntimeException exception) {
            removeAndClose(userId, emitter);
        }
        return emitter;
    }

    /** 向指定用户在当前实例上的全部连接投递同一条通知。 */
    public void sendToUser(Long userId, NotificationVO notification) {
        Set<SseEmitter> emitters = connections.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendNotification(userId, emitter, notification);
        }
    }

    /** 新连接根据 Last-Event-ID 补发历史通知时使用。 */
    public boolean sendReplay(
            Long userId,
            SseEmitter emitter,
            NotificationVO notification
    ) {
        return sendNotification(userId, emitter, notification);
    }

    /** 定时发送注释帧，避免代理和网关关闭长时间无数据的连接。 */
    @Scheduled(
            fixedDelayString =
                    "${app.realtime.heartbeat-interval-ms:25000}"
    )
    public void heartbeat() {
        connections.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(
                            SseEmitter.event().comment("heartbeat")
                    );
                } catch (IOException | RuntimeException exception) {
                    removeAndClose(userId, emitter);
                }
            }
        });
    }

    private boolean sendNotification(
            Long userId,
            SseEmitter emitter,
            NotificationVO notification
    ) {
        try {
            emitter.send(SseEmitter.event()
                    .id(notification.notificationId().toString())
                    .name(notification.notificationType().name())
                    .data(notification));
            return true;
        } catch (IOException | RuntimeException exception) {
            removeAndClose(userId, emitter);
            return false;
        }
    }

    private void removeAndClose(Long userId, SseEmitter emitter) {
        // send 已经失败时 Servlet 响应不可再写；只从注册表移除连接，
        // 不能调用 complete/completeWithError 再触发一次异步分派。
        remove(userId, emitter);
        LOGGER.debug("已移除不可用的SSE连接，userId={}", userId);
    }

    private void remove(Long userId, SseEmitter emitter) {
        connections.computeIfPresent(userId, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
