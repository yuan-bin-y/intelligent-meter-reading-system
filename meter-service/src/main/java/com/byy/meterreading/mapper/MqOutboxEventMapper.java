package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.MqOutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RabbitMQ Outbox 事件 Mapper。
 *
 * <p>插入使用 MyBatis-Plus；消息查询、抢占和状态更新使用带条件的 XML SQL，
 * 保证多个应用实例不会同时发送同一条消息。</p>
 */
@Mapper
public interface MqOutboxEventMapper
        extends BaseMapper<MqOutboxEvent> {

    /** 查询某个业务聚合最后创建的一条 Outbox 事件。 */
    MqOutboxEvent selectLatestByAggregate(
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") Long aggregateId
    );

    /**
     * 业务任务被取消时，终止尚未确认发送成功的消息。
     * 已经处于 SENT 的历史事件不会被修改。
     */
    int failUnsentByAggregate(
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") Long aggregateId,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /** 查询已经到发送时间的待发送事件，实际抢占仍由 claimEvent 完成。 */
    List<MqOutboxEvent> selectPublishableEvents(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    /** 原子抢占待发送事件，并使用 version 阻止多实例重复抢占。 */
    int claimEvent(
            @Param("eventId") Long eventId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("lockedBy") String lockedBy,
            @Param("lockedAt") LocalDateTime lockedAt
    );

    /** RabbitMQ 发布确认成功后，将事件标记为 SENT。 */
    int markSent(
            @Param("eventId") Long eventId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("sentAt") LocalDateTime sentAt
    );

    /** 发布失败但仍可重试时，恢复为 PENDING 并设置下次发送时间。 */
    int markRetry(
            @Param("eventId") Long eventId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("retryCount") Integer retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /** 超过最大重试次数后，将事件标记为最终失败。 */
    int markFailed(
            @Param("eventId") Long eventId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("retryCount") Integer retryCount,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /**
     * 应用实例在发送过程中崩溃时，释放超过锁定期限的 SENDING 事件，
     * 使其重新进入待发送状态。
     */
    int resetExpiredSendingEvents(
            @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
