ALTER TABLE ai_recognition_task
    DROP CHECK chk_ai_recognition_status_data,
    ADD COLUMN result_id BIGINT DEFAULT NULL
        COMMENT '识别成功后生成的待审核抄表结果主键'
        AFTER meter_id,
    ADD COLUMN cancel_reason VARCHAR(500) DEFAULT NULL
        COMMENT '管理员取消原因'
        AFTER processing_duration_ms,
    ADD COLUMN cancelled_by BIGINT DEFAULT NULL
        COMMENT '执行取消的管理员用户主键'
        AFTER cancel_reason,
    ADD COLUMN cancelled_at DATETIME DEFAULT NULL
        COMMENT '管理员取消时间'
        AFTER cancelled_by,
    ADD UNIQUE KEY uk_ai_recognition_result (result_id),
    ADD CONSTRAINT chk_ai_recognition_result_id
        CHECK (result_id IS NULL OR result_id > 0),
    ADD CONSTRAINT chk_ai_recognition_status_data
        CHECK (
            (
                status = 'PENDING'
                AND result_id IS NULL
                AND recognized_value IS NULL
                AND confidence IS NULL
                AND completed_at IS NULL
                AND cancel_reason IS NULL
                AND cancelled_by IS NULL
                AND cancelled_at IS NULL
            )
            OR (
                status = 'PROCESSING'
                AND started_at IS NOT NULL
                AND result_id IS NULL
                AND recognized_value IS NULL
                AND confidence IS NULL
                AND completed_at IS NULL
                AND cancel_reason IS NULL
                AND cancelled_by IS NULL
                AND cancelled_at IS NULL
            )
            OR (
                status = 'SUCCEEDED'
                AND result_id IS NOT NULL
                AND recognized_value IS NOT NULL
                AND confidence IS NOT NULL
                AND completed_at IS NOT NULL
                AND failure_code IS NULL
                AND failure_message IS NULL
                AND cancel_reason IS NULL
                AND cancelled_by IS NULL
                AND cancelled_at IS NULL
            )
            OR (
                status = 'FAILED'
                AND result_id IS NULL
                AND completed_at IS NOT NULL
                AND failure_message IS NOT NULL
                AND CHAR_LENGTH(TRIM(failure_message)) > 0
                AND cancel_reason IS NULL
                AND cancelled_by IS NULL
                AND cancelled_at IS NULL
            )
            OR (
                status = 'CANCELLED'
                AND result_id IS NULL
                AND completed_at IS NOT NULL
                AND cancel_reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(cancel_reason)) > 0
                AND cancelled_by IS NOT NULL
                AND cancelled_by > 0
                AND cancelled_at IS NOT NULL
            )
        );
