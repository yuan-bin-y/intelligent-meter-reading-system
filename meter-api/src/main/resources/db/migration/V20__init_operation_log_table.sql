CREATE TABLE operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作日志主键',
    trace_id VARCHAR(64) NOT NULL COMMENT '请求追踪标识',
    operator_id BIGINT NULL COMMENT '操作人用户ID，系统操作为空',
    operator_username VARCHAR(64) NULL COMMENT '操作发生时的用户名快照',
    module VARCHAR(64) NOT NULL COMMENT '业务模块',
    action VARCHAR(64) NOT NULL COMMENT '操作动作',
    resource_type VARCHAR(64) NULL COMMENT '被操作的业务资源类型',
    resource_id BIGINT NULL COMMENT '被操作的业务资源ID',
    request_method VARCHAR(16) NOT NULL COMMENT 'HTTP请求方法',
    request_path VARCHAR(255) NOT NULL COMMENT 'HTTP请求路径',
    client_ip VARCHAR(64) NULL COMMENT '客户端IP地址',
    request_params TEXT NULL COMMENT '脱敏并截断后的请求参数',
    result VARCHAR(16) NOT NULL COMMENT '操作结果：SUCCESS成功，FAILED失败',
    error_message VARCHAR(500) NULL COMMENT '失败时的安全错误说明',
    duration_ms BIGINT NOT NULL COMMENT '执行耗时，单位毫秒',
    created_at DATETIME NOT NULL COMMENT '操作发生时间',
    PRIMARY KEY (id),
    KEY idx_operation_log_created_at (created_at),
    KEY idx_operation_log_operator_created_at (operator_id, created_at),
    CONSTRAINT chk_operation_log_result
        CHECK (result IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='后台操作审计日志表';
