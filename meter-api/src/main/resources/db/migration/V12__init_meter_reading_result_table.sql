CREATE TABLE meter_reading_result
(
    id                     BIGINT         NOT NULL AUTO_INCREMENT COMMENT '抄表结果主键',
    task_id                BIGINT         NOT NULL COMMENT '关联抄表任务主键',
    meter_id               BIGINT         NOT NULL COMMENT '关联表具主键',
    source_type            VARCHAR(32)    NOT NULL COMMENT '结果来源：METER_READER-抄表员，DEVICE-采集设备',
    meter_reader_id        BIGINT                  DEFAULT NULL COMMENT '人工抄表员用户主键',
    device_id              BIGINT                  DEFAULT NULL COMMENT '自动抄表设备主键',
    reading_value          DECIMAL(18, 3) NOT NULL COMMENT '本次提交的表具读数',
    image_url              VARCHAR(1024)  NOT NULL COMMENT '抄表照片或设备采集图片地址',
    recognition_confidence DECIMAL(5, 4)           DEFAULT NULL COMMENT '设备识别置信度，人工提交时为空',
    remark                 VARCHAR(500)            DEFAULT NULL COMMENT '人工备注或设备附加信息',
    review_status          VARCHAR(32)    NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING-待审核，APPROVED-通过，REJECTED-驳回',
    submitted_at           DATETIME       NOT NULL COMMENT '结果提交时间',
    version                INT            NOT NULL DEFAULT 0 COMMENT '结果审核阶段使用的乐观锁版本号',
    created_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meter_reading_result_task (task_id),
    KEY idx_meter_reading_result_meter_submitted (meter_id, submitted_at),
    KEY idx_meter_reading_result_review_submitted (review_status, submitted_at),
    CONSTRAINT chk_meter_reading_result_task_id
        CHECK (task_id > 0),
    CONSTRAINT chk_meter_reading_result_meter_id
        CHECK (meter_id > 0),
    CONSTRAINT chk_meter_reading_result_source_type
        CHECK (source_type IN ('METER_READER', 'DEVICE')),
    CONSTRAINT chk_meter_reading_result_executor
        CHECK (
            (
                source_type = 'METER_READER'
                AND meter_reader_id IS NOT NULL
                AND meter_reader_id > 0
                AND device_id IS NULL
                AND recognition_confidence IS NULL
            )
            OR
            (
                source_type = 'DEVICE'
                AND device_id IS NOT NULL
                AND device_id > 0
                AND meter_reader_id IS NULL
                AND recognition_confidence IS NOT NULL
            )
        ),
    CONSTRAINT chk_meter_reading_result_reading
        CHECK (reading_value >= 0),
    CONSTRAINT chk_meter_reading_result_confidence
        CHECK (
            recognition_confidence IS NULL
            OR recognition_confidence BETWEEN 0 AND 1
        ),
    CONSTRAINT chk_meter_reading_result_review_status
        CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_meter_reading_result_version
        CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '抄表结果及待审核数据表';
