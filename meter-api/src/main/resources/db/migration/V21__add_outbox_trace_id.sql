-- Outbox 必须保存业务请求的 traceId，消息延迟发布后才能继续原调用链。
ALTER TABLE mq_outbox_event
    ADD COLUMN trace_id VARCHAR(64) NULL
        COMMENT '创建该业务事件时的追踪标识'
        AFTER event_id;

-- 为升级前已经存在的事件补充独立追踪标识，避免历史数据阻止非空约束。
UPDATE mq_outbox_event
SET trace_id = LOWER(REPLACE(UUID(), '-', ''))
WHERE trace_id IS NULL;

ALTER TABLE mq_outbox_event
    MODIFY COLUMN trace_id VARCHAR(64) NOT NULL
        COMMENT '创建该业务事件时的追踪标识',
    ADD KEY idx_mq_outbox_trace_id (trace_id);
