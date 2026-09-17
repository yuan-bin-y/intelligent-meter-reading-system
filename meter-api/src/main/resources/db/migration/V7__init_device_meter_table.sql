CREATE TABLE device_meter
(
    device_id  BIGINT   NOT NULL COMMENT '设备主键',
    meter_id   BIGINT   NOT NULL COMMENT '表具主键',
    created_by BIGINT   NOT NULL COMMENT '创建绑定关系的管理员用户主键',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (device_id, meter_id),
    KEY idx_device_meter_meter_id (meter_id, device_id),
    KEY idx_device_meter_created_at (created_at),
    CONSTRAINT chk_device_meter_device_id CHECK (device_id > 0),
    CONSTRAINT chk_device_meter_meter_id CHECK (meter_id > 0),
    CONSTRAINT chk_device_meter_created_by CHECK (created_by > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '设备与表具绑定关系表';
