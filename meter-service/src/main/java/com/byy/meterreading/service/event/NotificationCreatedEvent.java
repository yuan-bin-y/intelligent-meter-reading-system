package com.byy.meterreading.service.event;

/** 通知记录成功落库后，用于触发跨实例实时投递。 */
public record NotificationCreatedEvent(
        Long notificationId,
        Long recipientUserId
) {
}
