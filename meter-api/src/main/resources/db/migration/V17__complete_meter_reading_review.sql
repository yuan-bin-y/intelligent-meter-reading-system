ALTER TABLE meter_reading_result
    ADD COLUMN confirmed_reading_value DECIMAL(18, 3) NULL
        COMMENT '审核确认后的最终读数；只有审核通过时有值'
        AFTER reading_value;

-- 兼容已经由旧版本审核通过的数据，保留原提交值作为最终确认值。
UPDATE meter_reading_result
SET confirmed_reading_value = reading_value
WHERE review_status = 'APPROVED'
  AND confirmed_reading_value IS NULL;

ALTER TABLE meter_reading_result
    ADD CONSTRAINT chk_meter_reading_result_confirmed_reading
        CHECK (
            (review_status = 'PENDING' AND confirmed_reading_value IS NULL)
            OR (
                review_status = 'APPROVED'
                AND confirmed_reading_value IS NOT NULL
                AND confirmed_reading_value >= 0
            )
            OR (review_status = 'REJECTED' AND confirmed_reading_value IS NULL)
        );

ALTER TABLE meter_reading_review
    ADD COLUMN submitted_reading_value DECIMAL(18, 3) NULL
        COMMENT '审核时保存的原始提交读数快照'
        AFTER reviewer_id,
    ADD COLUMN confirmed_reading_value DECIMAL(18, 3) NULL
        COMMENT '审核确认读数；驳回时为空'
        AFTER submitted_reading_value;

-- 为旧审核记录补齐快照，迁移完成后改为非空。
UPDATE meter_reading_review AS review
INNER JOIN meter_reading_result AS result ON result.id = review.result_id
SET review.submitted_reading_value = result.reading_value,
    review.confirmed_reading_value = CASE
        WHEN review.review_action = 'APPROVED'
            THEN result.confirmed_reading_value
        ELSE NULL
    END;

ALTER TABLE meter_reading_review
    MODIFY COLUMN submitted_reading_value DECIMAL(18, 3) NOT NULL
        COMMENT '审核时保存的原始提交读数快照',
    ADD CONSTRAINT chk_meter_reading_review_submitted_reading
        CHECK (submitted_reading_value >= 0),
    ADD CONSTRAINT chk_meter_reading_review_confirmed_reading
        CHECK (
            (
                review_action = 'APPROVED'
                AND confirmed_reading_value IS NOT NULL
                AND confirmed_reading_value >= 0
            )
            OR (
                review_action = 'REJECTED'
                AND confirmed_reading_value IS NULL
            )
        );
