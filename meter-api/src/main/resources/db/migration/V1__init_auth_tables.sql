CREATE TABLE sys_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    username      VARCHAR(64)  NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    display_name  VARCHAR(64)  NOT NULL COMMENT '显示名称',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统用户表';

CREATE TABLE sys_role
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '角色主键',
    role_code  VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name  VARCHAR(64) NOT NULL COMMENT '角色名称',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_role_code (role_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统角色表';

CREATE TABLE sys_user_role
(
    user_id    BIGINT   NOT NULL COMMENT '用户主键',
    role_id    BIGINT   NOT NULL COMMENT '角色主键',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role_id (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户角色关联表';

INSERT INTO sys_role (role_code, role_name, status, created_at, updated_at)
VALUES ('ADMIN', '管理员', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('METER_READER', '抄表员', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('AUDITOR', '审核员', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('RESIDENT', '居民', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
