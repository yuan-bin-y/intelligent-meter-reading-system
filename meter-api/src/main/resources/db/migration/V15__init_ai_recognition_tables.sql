CREATE TABLE ai_recognition_task
(
    id                     BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'AI识别任务主键',
    recognition_no         VARCHAR(64)    NOT NULL COMMENT 'AI识别任务编号',
    reading_task_id        BIGINT         NOT NULL COMMENT '关联抄表任务主键',
    image_id               BIGINT         NOT NULL COMMENT '关联待识别抄表图片主键',
    meter_id               BIGINT         NOT NULL COMMENT '关联表具主键',
    attempt_no             INT            NOT NULL COMMENT '同一图片的识别尝试序号，从1开始',
    status                 VARCHAR(32)    NOT NULL DEFAULT 'PENDING'
                                                   COMMENT '状态：PENDING、PROCESSING、SUCCEEDED、FAILED、CANCELLED',
    recognized_value       DECIMAL(18, 3)          DEFAULT NULL COMMENT 'AI识别出的表具读数',
    confidence             DECIMAL(5, 4)           DEFAULT NULL COMMENT 'AI识别置信度，范围0到1',
    model_name             VARCHAR(128)            DEFAULT NULL COMMENT '识别模型名称',
    model_version          VARCHAR(64)             DEFAULT NULL COMMENT '识别模型版本',
    raw_result             JSON                    DEFAULT NULL COMMENT 'AI服务返回的原始识别结果',
    failure_code           VARCHAR(64)             DEFAULT NULL COMMENT '识别失败编码',
    failure_message        VARCHAR(500)            DEFAULT NULL COMMENT '识别失败原因',
    retry_count            INT            NOT NULL DEFAULT 0 COMMENT '已自动重试次数',
    max_retry_count        INT            NOT NULL DEFAULT 3 COMMENT '最大自动重试次数',
    started_at             DATETIME                DEFAULT NULL COMMENT '开始识别时间',
    completed_at           DATETIME                DEFAULT NULL COMMENT '识别完成、失败或取消时间',
    processing_duration_ms BIGINT                  DEFAULT NULL COMMENT 'AI识别耗时，单位毫秒',
    version                INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_recognition_task_no (recognition_no),
    UNIQUE KEY uk_ai_recognition_image_attempt (image_id, attempt_no),
    KEY idx_ai_recognition_reading_task (reading_task_id),
    KEY idx_ai_recognition_meter_created (meter_id, created_at),
    KEY idx_ai_recognition_status_created (status, created_at),
    CONSTRAINT chk_ai_recognition_reading_task_id CHECK (reading_task_id > 0),
    CONSTRAINT chk_ai_recognition_image_id CHECK (image_id > 0),
    CONSTRAINT chk_ai_recognition_meter_id CHECK (meter_id > 0),
    CONSTRAINT chk_ai_recognition_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT chk_ai_recognition_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_ai_recognition_value
        CHECK (recognized_value IS NULL OR recognized_value >= 0),
    CONSTRAINT chk_ai_recognition_confidence
        CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_ai_recognition_retry
        CHECK (
            retry_count >= 0
            AND max_retry_count >= 0
            AND retry_count <= max_retry_count
        ),
    CONSTRAINT chk_ai_recognition_duration
        CHECK (processing_duration_ms IS NULL OR processing_duration_ms >= 0),
    CONSTRAINT chk_ai_recognition_version CHECK (version >= 0),
    CONSTRAINT chk_ai_recognition_status_data
        CHECK (
            (
                status = 'PENDING'
                AND recognized_value IS NULL
                AND confidence IS NULL
                AND completed_at IS NULL
            )
            OR (
                status = 'PROCESSING'
                AND started_at IS NOT NULL
                AND recognized_value IS NULL
                AND confidence IS NULL
                AND completed_at IS NULL
            )
            OR (
                status = 'SUCCEEDED'
                AND recognized_value IS NOT NULL
                AND confidence IS NOT NULL
                AND completed_at IS NOT NULL
                AND failure_code IS NULL
                AND failure_message IS NULL
            )
            OR (
                status = 'FAILED'
                AND completed_at IS NOT NULL
                AND failure_message IS NOT NULL
                AND CHAR_LENGTH(TRIM(failure_message)) > 0
            )
            OR (
                status = 'CANCELLED'
                AND completed_at IS NOT NULL
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'AI表具读数识别任务表';

CREATE TABLE mq_outbox_event
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Outbox事件主键',
    event_id       VARCHAR(64)  NOT NULL COMMENT '消息唯一编号，用于生产端和消费端幂等',
    aggregate_type VARCHAR(64)  NOT NULL COMMENT '业务聚合类型，例如AI_RECOGNITION_TASK',
    aggregate_id   BIGINT       NOT NULL COMMENT '业务聚合主键，例如AI识别任务主键',
    event_type     VARCHAR(64)  NOT NULL COMMENT '事件类型，例如RECOGNITION_TASK_CREATED',
    exchange_name  VARCHAR(128) NOT NULL COMMENT 'RabbitMQ交换机名称',
    routing_key    VARCHAR(128) NOT NULL COMMENT 'RabbitMQ路由键',
    payload        JSON         NOT NULL COMMENT '待发送的消息内容',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING'
                                             COMMENT '发送状态：PENDING、SENDING、SENT、FAILED',
    retry_count    INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT         NOT NULL DEFAULT 5 COMMENT '最大发送重试次数',
    next_retry_at  DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '下次允许发送时间',
    locked_by      VARCHAR(128)          DEFAULT NULL COMMENT '当前锁定消息的应用实例',
    locked_at      DATETIME              DEFAULT NULL COMMENT '消息锁定时间',
    sent_at        DATETIME              DEFAULT NULL COMMENT 'RabbitMQ确认接收时间',
    last_error     VARCHAR(1000)         DEFAULT NULL COMMENT '最后一次发送失败原因',
    version        INT          NOT NULL DEFAULT 0 COMMENT '抢占和更新使用的乐观锁版本号',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                           ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mq_outbox_event_id (event_id),
    KEY idx_mq_outbox_status_retry (status, next_retry_at),
    KEY idx_mq_outbox_aggregate (aggregate_type, aggregate_id),
    KEY idx_mq_outbox_created (created_at),
    CONSTRAINT chk_mq_outbox_aggregate_id CHECK (aggregate_id > 0),
    CONSTRAINT chk_mq_outbox_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    CONSTRAINT chk_mq_outbox_retry
        CHECK (
            retry_count >= 0
            AND max_retry_count >= 0
            AND retry_count <= max_retry_count
        ),
    CONSTRAINT chk_mq_outbox_version CHECK (version >= 0),
    CONSTRAINT chk_mq_outbox_lock
        CHECK (
            (locked_by IS NULL AND locked_at IS NULL)
            OR (
                locked_by IS NOT NULL
                AND CHAR_LENGTH(TRIM(locked_by)) > 0
                AND locked_at IS NOT NULL
            )
        ),
    CONSTRAINT chk_mq_outbox_status_data
        CHECK (
            (
                status = 'PENDING'
                AND sent_at IS NULL
            )
            OR (
                status = 'SENDING'
                AND locked_by IS NOT NULL
                AND locked_at IS NOT NULL
                AND sent_at IS NULL
            )
            OR (
                status = 'SENT'
                AND sent_at IS NOT NULL
            )
            OR (
                status = 'FAILED'
                AND sent_at IS NULL
                AND last_error IS NOT NULL
                AND CHAR_LENGTH(TRIM(last_error)) > 0
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'RabbitMQ事务消息Outbox事件表';
