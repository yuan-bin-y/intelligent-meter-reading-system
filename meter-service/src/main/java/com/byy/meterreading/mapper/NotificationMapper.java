package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.mapper.projection.NotificationRow;
import com.byy.meterreading.model.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 通知单表写入及关联触发用户的查询 Mapper。 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    IPage<NotificationRow> selectNotificationPage(
            Page<NotificationRow> page,
            @Param("recipientUserId") Long recipientUserId,
            @Param("unreadOnly") boolean unreadOnly
    );

    NotificationRow selectViewForRecipient(
            @Param("notificationId") Long notificationId,
            @Param("recipientUserId") Long recipientUserId
    );

    Notification selectByDedupKey(@Param("dedupKey") String dedupKey);

    long countUnread(@Param("recipientUserId") Long recipientUserId);

    int markRead(
            @Param("notificationId") Long notificationId,
            @Param("recipientUserId") Long recipientUserId
    );

    int markAllRead(@Param("recipientUserId") Long recipientUserId);

    List<NotificationRow> selectAfterId(
            @Param("recipientUserId") Long recipientUserId,
            @Param("lastEventId") Long lastEventId,
            @Param("limit") int limit
    );
}
