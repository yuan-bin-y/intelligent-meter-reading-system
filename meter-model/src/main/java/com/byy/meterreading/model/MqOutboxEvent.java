package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RabbitMQ 事务消息 Outbox 事件实体，对应 mq_outbox_event 表。
 *
 * <p>业务事务只负责写入该表，后台发布器随后读取待发送事件并投递到
 * RabbitMQ，从而避免业务数据已经提交但消息丢失。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("mq_outbox_event")
public class MqOutboxEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 消息唯一编号，同时用于生产端和消费端幂等。 */
    private String eventId;

    /** 创建业务事件时的 traceId，随 RabbitMQ 消息继续传递。 */
    private String traceId;

    /** 对应业务聚合，例如 AI_RECOGNITION_TASK 及其任务主键。 */
    private String aggregateType;
    private Long aggregateId;
    private String eventType;

    private String exchangeName;
    private String routingKey;

    /** 实际发送给 RabbitMQ 的 JSON 消息内容。 */
    private String payload;

    /** PENDING、SENDING、SENT 或 FAILED。 */
    private String status;

    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextRetryAt;

    /** 多实例并发发布消息时的抢占实例和抢占时间。 */
    private String lockedBy;
    private LocalDateTime lockedAt;

    private LocalDateTime sentAt;
    private String lastError;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
