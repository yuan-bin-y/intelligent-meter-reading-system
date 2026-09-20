ALTER TABLE meter_reading_result
    DROP INDEX uk_meter_reading_result_task,
    ADD COLUMN attempt_no INT NOT NULL DEFAULT 1
        COMMENT '同一任务的提交尝试序号，从1开始'
        AFTER task_id,
    ADD UNIQUE KEY uk_meter_reading_result_task_attempt
        (task_id, attempt_no),
    ADD CONSTRAINT chk_meter_reading_result_attempt_no
        CHECK (attempt_no > 0);

CREATE TABLE meter_reading_review
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审核记录主键',
    result_id    BIGINT       NOT NULL COMMENT '被审核的抄表结果主键',
    task_id      BIGINT       NOT NULL COMMENT '关联抄表任务主键',
    review_action VARCHAR(32)  NOT NULL COMMENT '审核动作：APPROVED、REJECTED',
    reviewer_id  BIGINT       NOT NULL COMMENT '审核人用户主键',
    review_reason VARCHAR(500)          DEFAULT NULL COMMENT '审核说明或驳回原因',
    reviewed_at  DATETIME     NOT NULL COMMENT '审核时间',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meter_reading_review_result (result_id),
    KEY idx_meter_reading_review_task_time (task_id, reviewed_at),
    KEY idx_meter_reading_review_reviewer_time (reviewer_id, reviewed_at),
    CONSTRAINT chk_meter_reading_review_result_id CHECK (result_id > 0),
    CONSTRAINT chk_meter_reading_review_task_id CHECK (task_id > 0),
    CONSTRAINT chk_meter_reading_review_reviewer_id CHECK (reviewer_id > 0),
    CONSTRAINT chk_meter_reading_review_action
        CHECK (review_action IN ('APPROVED', 'REJECTED')),
    CONSTRAINT chk_meter_reading_review_reason
        CHECK (
            review_action = 'APPROVED'
            OR (
                review_action = 'REJECTED'
                AND review_reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(review_reason)) > 0
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '抄表结果审核历史表';

CREATE TABLE meter_reading_record
(
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '正式抄表记录主键',
    result_id     BIGINT         NOT NULL COMMENT '审核通过的抄表结果主键',
    task_id       BIGINT         NOT NULL COMMENT '关联抄表任务主键',
    meter_id      BIGINT         NOT NULL COMMENT '关联表具主键',
    reading_value DECIMAL(18, 3) NOT NULL COMMENT '审核通过的正式读数',
    reading_at    DATETIME       NOT NULL COMMENT '实际结果提交时间',
    source_type   VARCHAR(32)    NOT NULL COMMENT '来源：METER_READER、DEVICE',
    executor_id   BIGINT         NOT NULL COMMENT '抄表员用户主键或设备主键',
    reviewed_by   BIGINT         NOT NULL COMMENT '审核人用户主键',
    reviewed_at   DATETIME       NOT NULL COMMENT '审核通过时间',
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meter_reading_record_result (result_id),
    UNIQUE KEY uk_meter_reading_record_task (task_id),
    KEY idx_meter_reading_record_meter_time (meter_id, reading_at),
    KEY idx_meter_reading_record_reviewer_time (reviewed_by, reviewed_at),
    CONSTRAINT chk_meter_reading_record_result_id CHECK (result_id > 0),
    CONSTRAINT chk_meter_reading_record_task_id CHECK (task_id > 0),
    CONSTRAINT chk_meter_reading_record_meter_id CHECK (meter_id > 0),
    CONSTRAINT chk_meter_reading_record_reading CHECK (reading_value >= 0),
    CONSTRAINT chk_meter_reading_record_source_type
        CHECK (source_type IN ('METER_READER', 'DEVICE')),
    CONSTRAINT chk_meter_reading_record_executor_id CHECK (executor_id > 0),
    CONSTRAINT chk_meter_reading_record_reviewed_by CHECK (reviewed_by > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '审核通过后的正式抄表记录表';
