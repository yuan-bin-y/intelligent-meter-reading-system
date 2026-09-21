package com.byy.meterreading.vo.notification;

import com.byy.meterreading.model.enums.NotificationResourceType;
import com.byy.meterreading.model.enums.NotificationType;

import java.time.LocalDateTime;

/** 通知列表与 SSE data 共用的统一响应模型。 */
public record NotificationVO(
        Long notificationId,
        NotificationType notificationType,
        String notificationTypeName,
        Long actorUserId,
        String actorDisplayName,
        NotificationResourceType resourceType,
        String resourceTypeName,
        Long resourceId,
        String title,
        String content,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
