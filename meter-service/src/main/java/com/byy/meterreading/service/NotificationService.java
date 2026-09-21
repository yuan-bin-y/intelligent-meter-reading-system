package com.byy.meterreading.service;

import com.byy.meterreading.dto.notification.NotificationPageQueryDTO;
import com.byy.meterreading.service.command.CreateNotificationCommand;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.notification.NotificationUnreadCountVO;
import com.byy.meterreading.vo.notification.NotificationVO;

import java.util.List;

/** 通知持久化、查询、已读和断线补偿业务。 */
public interface NotificationService {

    PageVO<NotificationVO> list(
            Long userId,
            NotificationPageQueryDTO queryDTO
    );

    NotificationUnreadCountVO getUnreadCount(Long userId);

    NotificationVO markRead(Long userId, Long notificationId);

    NotificationUnreadCountVO markAllRead(Long userId);

    List<NotificationVO> replayAfter(
            Long userId,
            Long lastEventId,
            int limit
    );

    NotificationVO getForDelivery(
            Long notificationId,
            Long recipientUserId
    );

    NotificationVO create(CreateNotificationCommand command);
}
