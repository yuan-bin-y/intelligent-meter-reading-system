CREATE TABLE meter_reading_task
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '抄表任务主键',
    task_no           VARCHAR(64)  NOT NULL COMMENT '对外任务编号',
    meter_id          BIGINT       NOT NULL COMMENT '需要抄读的表具主键',
    executor_type     VARCHAR(32)  NOT NULL
                                              COMMENT '执行者类型：METER_READER-抄表员，DEVICE-采集设备',
    meter_reader_id   BIGINT                DEFAULT NULL COMMENT '执行任务的抄表员用户主键',
    device_id         BIGINT                DEFAULT NULL COMMENT '执行任务的采集设备主键',
    task_status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING'
                                              COMMENT '任务状态：PENDING-待执行，PROCESSING-执行中，PENDING_REVIEW-待审核，COMPLETED-已完成，FAILED-执行失败，CANCELLED-已取消',
    scheduled_at      DATETIME     NOT NULL COMMENT '计划执行时间',
    started_at        DATETIME              DEFAULT NULL COMMENT '实际开始时间',
    submitted_at      DATETIME              DEFAULT NULL COMMENT '结果提交时间',
    completed_at      DATETIME              DEFAULT NULL COMMENT '任务完成时间',
    failed_reason     VARCHAR(500)           DEFAULT NULL COMMENT '最近一次执行失败原因',
    retry_count       INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    version           INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    remark            VARCHAR(500)           DEFAULT NULL COMMENT '管理员备注',
    created_by        BIGINT       NOT NULL COMMENT '创建任务的管理员用户主键',
    updated_by        BIGINT       NOT NULL COMMENT '最后修改任务配置的用户主键',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                               ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meter_reading_task_task_no (task_no),
    KEY idx_meter_reading_task_status_scheduled (
        task_status,
        scheduled_at
    ),
    KEY idx_meter_reading_task_reader_status (
        meter_reader_id,
        task_status,
        scheduled_at
    ),
    KEY idx_meter_reading_task_device_status (
        device_id,
        task_status,
        scheduled_at
    ),
    KEY idx_meter_reading_task_meter_created (
        meter_id,
        created_at
    ),
    CONSTRAINT chk_meter_reading_task_meter_id
        CHECK (meter_id > 0),
    CONSTRAINT chk_meter_reading_task_executor_type
        CHECK (executor_type IN ('METER_READER', 'DEVICE')),
    CONSTRAINT chk_meter_reading_task_executor
        CHECK (
            (
                executor_type = 'METER_READER'
                AND meter_reader_id IS NOT NULL
                AND meter_reader_id > 0
                AND device_id IS NULL
            )
            OR
            (
                executor_type = 'DEVICE'
                AND device_id IS NOT NULL
                AND device_id > 0
                AND meter_reader_id IS NULL
            )
        ),
    CONSTRAINT chk_meter_reading_task_status
        CHECK (
            task_status IN (
                'PENDING',
                'PROCESSING',
                'PENDING_REVIEW',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            )
        ),
    CONSTRAINT chk_meter_reading_task_retry_count
        CHECK (retry_count >= 0),
    CONSTRAINT chk_meter_reading_task_version
        CHECK (version >= 0),
    CONSTRAINT chk_meter_reading_task_time_order
        CHECK (
            (started_at IS NULL OR started_at >= created_at)
            AND (submitted_at IS NULL OR started_at IS NOT NULL)
            AND (submitted_at IS NULL OR submitted_at >= started_at)
            AND (completed_at IS NULL OR submitted_at IS NOT NULL)
            AND (completed_at IS NULL OR completed_at >= submitted_at)
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '抄表任务表';
