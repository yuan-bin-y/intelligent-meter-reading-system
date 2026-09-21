package com.byy.meterreading.service.command;

import com.byy.meterreading.model.enums.NotificationResourceType;
import com.byy.meterreading.model.enums.NotificationType;

/**
 * 业务模块创建通知时使用的内部命令。
 *
 * @param dedupKey 相同业务事件的唯一键；为空时不执行去重
 */
public record CreateNotificationCommand(
        Long recipientUserId,
        Long actorUserId,
        NotificationType notificationType,
        NotificationResourceType resourceType,
        Long resourceId,
        String title,
        String content,
        String dedupKey
) {
}
