CREATE TABLE meter_image
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '抄表图片主键',
    task_id             BIGINT       NOT NULL COMMENT '所属抄表任务主键',
    uploader_type       VARCHAR(32)  NOT NULL COMMENT '上传者类型：METER_READER-抄表员，DEVICE-采集设备',
    uploader_id         BIGINT       NOT NULL COMMENT '抄表员用户主键或设备主键',
    upload_request_id   VARCHAR(64)  NOT NULL COMMENT '客户端上传幂等标识',
    image_type          VARCHAR(32)  NOT NULL COMMENT '图片类型：ORIGINAL、AI_ANNOTATED、ENVIRONMENT',
    original_name       VARCHAR(255) NOT NULL COMMENT '用户上传时的原始文件名',
    bucket_name         VARCHAR(64)  NOT NULL COMMENT '阿里云 OSS Bucket 名称',
    object_key          VARCHAR(512) NOT NULL COMMENT '阿里云 OSS 对象路径',
    oss_etag            VARCHAR(128)          DEFAULT NULL COMMENT 'OSS 上传返回的 ETag',
    content_type        VARCHAR(64)  NOT NULL COMMENT '服务端识别出的真实图片类型',
    file_size           BIGINT       NOT NULL COMMENT '图片大小，单位字节',
    image_width         INT                   DEFAULT NULL COMMENT '图片宽度；无法读取时为空',
    image_height        INT                   DEFAULT NULL COMMENT '图片高度；无法读取时为空',
    sha256              CHAR(64)     NOT NULL COMMENT '图片内容 SHA-256 摘要',
    image_status        VARCHAR(32)  NOT NULL DEFAULT 'VALID' COMMENT '业务状态：VALID-有效，INVALID-无效',
    storage_status      VARCHAR(32)  NOT NULL DEFAULT 'STORED' COMMENT 'OSS 状态：STORED、DELETE_PENDING、DELETED',
    status_reason       VARCHAR(500)          DEFAULT NULL COMMENT '最近一次失效或恢复原因',
    status_changed_by   BIGINT                DEFAULT NULL COMMENT '最近一次状态操作管理员主键',
    status_changed_at   DATETIME              DEFAULT NULL COMMENT '最近一次状态操作时间',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '上传者软删除：0-未删除，1-已删除',
    deleted_by          BIGINT                DEFAULT NULL COMMENT '执行软删除的抄表员用户主键',
    deleted_at          DATETIME              DEFAULT NULL COMMENT '软删除时间',
    version             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                             ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meter_image_object (bucket_name, object_key),
    UNIQUE KEY uk_meter_image_upload_request
        (uploader_type, uploader_id, upload_request_id),
    KEY idx_meter_image_task_created (task_id, created_at),
    KEY idx_meter_image_uploader_created
        (uploader_type, uploader_id, created_at),
    KEY idx_meter_image_status_created
        (image_status, storage_status, created_at),
    KEY idx_meter_image_sha256 (sha256),
    CONSTRAINT chk_meter_image_task_id CHECK (task_id > 0),
    CONSTRAINT chk_meter_image_uploader_id CHECK (uploader_id > 0),
    CONSTRAINT chk_meter_image_uploader_type
        CHECK (uploader_type IN ('METER_READER', 'DEVICE')),
    CONSTRAINT chk_meter_image_type
        CHECK (image_type IN ('ORIGINAL', 'AI_ANNOTATED', 'ENVIRONMENT')),
    CONSTRAINT chk_meter_image_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_meter_image_size CHECK (file_size > 0),
    CONSTRAINT chk_meter_image_width
        CHECK (image_width IS NULL OR image_width > 0),
    CONSTRAINT chk_meter_image_height
        CHECK (image_height IS NULL OR image_height > 0),
    CONSTRAINT chk_meter_image_status
        CHECK (image_status IN ('VALID', 'INVALID')),
    CONSTRAINT chk_meter_image_storage_status
        CHECK (storage_status IN ('STORED', 'DELETE_PENDING', 'DELETED')),
    CONSTRAINT chk_meter_image_status_operation
        CHECK (
            (
                status_reason IS NULL
                AND status_changed_by IS NULL
                AND status_changed_at IS NULL
            )
            OR
            (
                status_reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(status_reason)) > 0
                AND status_changed_by IS NOT NULL
                AND status_changed_by > 0
                AND status_changed_at IS NOT NULL
            )
        ),
    CONSTRAINT chk_meter_image_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_meter_image_delete_operation
        CHECK (
            (
                deleted = 0
                AND deleted_by IS NULL
                AND deleted_at IS NULL
            )
            OR
            (
                deleted = 1
                AND deleted_by IS NOT NULL
                AND deleted_by > 0
                AND deleted_at IS NOT NULL
                AND storage_status IN ('DELETE_PENDING', 'DELETED')
            )
        ),
    CONSTRAINT chk_meter_image_version CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '抄表图片 OSS 元数据表';

CREATE TABLE meter_reading_result_image
(
    result_id  BIGINT   NOT NULL COMMENT '抄表结果主键',
    image_id   BIGINT   NOT NULL COMMENT '抄表图片主键',
    sort_order INT      NOT NULL COMMENT '结果内展示顺序，从0开始',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
    PRIMARY KEY (result_id, image_id),
    UNIQUE KEY uk_result_image_image (image_id),
    UNIQUE KEY uk_result_image_order (result_id, sort_order),
    KEY idx_result_image_created (created_at),
    CONSTRAINT chk_result_image_result_id CHECK (result_id > 0),
    CONSTRAINT chk_result_image_image_id CHECK (image_id > 0),
    CONSTRAINT chk_result_image_sort_order CHECK (sort_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '抄表结果与图片关联表';

ALTER TABLE meter_reading_result
    MODIFY COLUMN image_url VARCHAR(1024) NULL
        COMMENT '旧版图片地址；OSS 图片模块启用后不再写入';
