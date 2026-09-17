CREATE TABLE meter
(
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '表具主键',
    meter_no        VARCHAR(64)    NOT NULL COMMENT '表具编号',
    meter_name      VARCHAR(64)    NOT NULL COMMENT '表具名称',
    meter_type      VARCHAR(32)    NOT NULL COMMENT '表具类型：WATER-水表，ELECTRIC-电表，GAS-燃气表',
    display_type    VARCHAR(32)    NOT NULL COMMENT '显示类型：LCD-液晶显示型，MECHANICAL_ROLLER-机械滚轮型',
    unit            VARCHAR(16)    NOT NULL COMMENT '计量单位',
    integer_digits  TINYINT        NOT NULL COMMENT '读数整数位数',
    decimal_digits  TINYINT        NOT NULL DEFAULT 0 COMMENT '读数小数位数',
    initial_reading DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '安装初始读数',
    installed_at    DATE                    DEFAULT NULL COMMENT '安装日期',
    status          TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-正常，2-维护中，3-已报废',
    version         INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    remark          VARCHAR(500)            DEFAULT NULL COMMENT '备注',
    created_by      BIGINT         NOT NULL COMMENT '创建人用户主键',
    updated_by      BIGINT         NOT NULL COMMENT '最后修改人用户主键',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                             ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meter_meter_no (meter_no),
    KEY idx_meter_deleted_status (deleted, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '表具档案表';
