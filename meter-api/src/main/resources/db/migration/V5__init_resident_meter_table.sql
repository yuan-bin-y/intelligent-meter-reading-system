CREATE TABLE resident_meter
(
    resident_id BIGINT   NOT NULL COMMENT '居民用户主键',
    meter_id    BIGINT   NOT NULL COMMENT '表具主键',
    created_by  BIGINT   NOT NULL COMMENT '创建绑定关系的管理员用户主键',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (resident_id, meter_id),
    KEY idx_resident_meter_meter_id (meter_id, resident_id),
    KEY idx_resident_meter_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '居民与表具绑定关系表';
