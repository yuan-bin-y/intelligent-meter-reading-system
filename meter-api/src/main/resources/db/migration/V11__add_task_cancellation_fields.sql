ALTER TABLE meter_reading_task
    ADD COLUMN cancel_reason VARCHAR(500) DEFAULT NULL
        COMMENT '管理员取消任务的原因'
        AFTER failed_reason,
    ADD COLUMN cancelled_at DATETIME DEFAULT NULL
        COMMENT '任务取消时间'
        AFTER cancel_reason,
    ADD CONSTRAINT chk_meter_reading_task_cancellation
        CHECK (
            (
                task_status = 'CANCELLED'
                AND cancel_reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(cancel_reason)) > 0
                AND cancelled_at IS NOT NULL
                AND cancelled_at >= created_at
            )
            OR
            (
                task_status <> 'CANCELLED'
                AND cancel_reason IS NULL
                AND cancelled_at IS NULL
            )
        );
