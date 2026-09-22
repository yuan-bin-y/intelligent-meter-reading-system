package com.byy.meterreading.realtime.redis;

/** Redis 频道传递通知定位信息和原业务 traceId，完整内容仍从 MySQL 查询。 */
public record RealtimeRedisMessage(
        Long notificationId,
        Long recipientUserId,
        String traceId
) {
}
