CREATE TABLE device_alarm
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '设备告警主键',
    device_id     BIGINT      NOT NULL COMMENT '发生告警的设备主键',
    alarm_type    VARCHAR(32) NOT NULL COMMENT '告警类型：OFFLINE-设备离线',
    alarm_status  VARCHAR(16) NOT NULL DEFAULT 'OPEN'
                                         COMMENT '告警状态：OPEN-未恢复，RECOVERED-已恢复',
    occurred_at   DATETIME    NOT NULL COMMENT '检测到告警的时间',
    recovered_at  DATETIME             DEFAULT NULL COMMENT '设备恢复时间',
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE
            WHEN alarm_status = 'OPEN' THEN 1
            ELSE NULL
        END
    ) STORED COMMENT '未恢复告警唯一标记',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_alarm_active (
        device_id,
        alarm_type,
        active_marker
    ),
    KEY idx_device_alarm_status_occurred (
        alarm_status,
        occurred_at
    ),
    KEY idx_device_alarm_device_occurred (
        device_id,
        occurred_at
    ),
    CONSTRAINT chk_device_alarm_device_id
        CHECK (device_id > 0),
    CONSTRAINT chk_device_alarm_type
        CHECK (alarm_type IN ('OFFLINE')),
    CONSTRAINT chk_device_alarm_status
        CHECK (alarm_status IN ('OPEN', 'RECOVERED')),
    CONSTRAINT chk_device_alarm_recovery
        CHECK (
            (alarm_status = 'OPEN' AND recovered_at IS NULL)
            OR
            (alarm_status = 'RECOVERED' AND recovered_at IS NOT NULL)
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '设备告警记录表';
