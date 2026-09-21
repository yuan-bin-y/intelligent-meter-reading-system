package com.byy.meterreading.mapper.projection;

import lombok.Data;

import java.time.LocalDateTime;

/** 通知表关联触发用户显示名称后的查询投影。 */
@Data
public class NotificationRow {
    private Long notificationId;
    private Long recipientUserId;
    private Long actorUserId;
    private String actorDisplayName;
    private String notificationType;
    private String resourceType;
    private Long resourceId;
    private String title;
    private String content;
    private Integer isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
