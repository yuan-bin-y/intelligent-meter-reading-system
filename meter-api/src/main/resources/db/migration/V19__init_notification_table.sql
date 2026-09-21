CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知主键',
    recipient_user_id BIGINT NOT NULL COMMENT '接收用户ID',
    actor_user_id BIGINT NULL COMMENT '触发通知的用户ID，系统通知为空',
    notification_type VARCHAR(64) NOT NULL COMMENT '通知类型',
    resource_type VARCHAR(64) NOT NULL COMMENT '关联业务资源类型',
    resource_id BIGINT NULL COMMENT '关联业务资源ID',
    title VARCHAR(128) NOT NULL COMMENT '通知标题',
    content VARCHAR(500) NOT NULL COMMENT '通知内容',
    dedup_key VARCHAR(128) NULL COMMENT '业务幂等键',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0未读，1已读',
    read_at DATETIME NULL COMMENT '读取时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_dedup_key (dedup_key),
    KEY idx_notification_recipient_read_id (recipient_user_id, is_read, id),
    KEY idx_notification_resource (resource_type, resource_id),
    KEY idx_notification_created_at (created_at),
    CONSTRAINT chk_notification_is_read CHECK (is_read IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户通知表';
