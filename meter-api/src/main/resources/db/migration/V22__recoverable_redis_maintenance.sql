CREATE TABLE device_credential_rotation (
    device_id BIGINT NOT NULL COMMENT '设备ID',
    request_id CHAR(36) NOT NULL COMMENT '客户端生成的幂等请求UUID',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    expected_version INT NOT NULL COMMENT '请求中的设备版本',
    credential_version INT NOT NULL COMMENT '轮换后的凭证版本',
    device_version INT NOT NULL COMMENT '轮换后的设备版本',
    rotated_at DATETIME(6) NOT NULL COMMENT '轮换提交时间',
    PRIMARY KEY (device_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密钥轮换结果，不存储明文密钥';

CREATE TABLE redis_maintenance_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    task_type VARCHAR(32) NOT NULL COMMENT '清理操作类型',
    resource_key VARCHAR(255) NOT NULL COMMENT '设备ID或缓存Key',
    target_version INT NOT NULL DEFAULT 0 COMMENT '低于此版本的心跳需要清理',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE',
    attempts INT NOT NULL DEFAULT 0 COMMENT '尝试次数',
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次执行时间',
    lease_token CHAR(36) NULL COMMENT '本次领取令牌',
    lease_until DATETIME(6) NULL COMMENT '领取有效期',
    last_error VARCHAR(512) NULL COMMENT '最近失败摘要，不含业务密钥',
    trace_id CHAR(32) NOT NULL COMMENT '业务追踪ID',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '修改时间',
    PRIMARY KEY (id),
    INDEX idx_redis_maintenance_due (status, next_attempt_at, id),
    INDEX idx_redis_maintenance_lease (status, lease_until),
    CONSTRAINT chk_redis_maintenance_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='与业务事务一起提交的Redis清理任务';
