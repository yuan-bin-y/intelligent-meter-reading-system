package com.byy.meterreading.realtime.redis;

/** Redis 频道只传通知定位信息，完整内容仍从 MySQL 查询。 */
public record RealtimeRedisMessage(
        Long notificationId,
        Long recipientUserId
) {
}
