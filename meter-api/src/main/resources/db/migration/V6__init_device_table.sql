CREATE TABLE device
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '设备主键',
    device_no   VARCHAR(64)  NOT NULL COMMENT '设备编号',
    device_name VARCHAR(64)  NOT NULL COMMENT '设备名称',
    device_type VARCHAR(32)  NOT NULL COMMENT '设备类型：CAMERA-摄像头，GATEWAY-网关，EDGE_DEVICE-边缘设备',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-停用，1-启用',
    version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    remark      VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    created_by  BIGINT       NOT NULL COMMENT '创建人用户主键',
    updated_by  BIGINT       NOT NULL COMMENT '最后修改人用户主键',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                       ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_device_no (device_no),
    KEY idx_device_deleted_status (deleted, status),
    CONSTRAINT chk_device_type
        CHECK (device_type IN ('CAMERA', 'GATEWAY', 'EDGE_DEVICE')),
    CONSTRAINT chk_device_status
        CHECK (status IN (0, 1)),
    CONSTRAINT chk_device_version
        CHECK (version >= 0),
    CONSTRAINT chk_device_deleted
        CHECK (deleted IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '采集设备档案表';
