CREATE TABLE chat_conversation
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '聊天会话主键',
    conversation_type VARCHAR(32)  NOT NULL COMMENT '会话类型：TASK_SERVICE-任务沟通，ADMIN_SUPPORT-管理员客服',
    task_id           BIGINT                DEFAULT NULL COMMENT '关联抄表任务；管理员客服会话为空',
    subject           VARCHAR(128) NOT NULL COMMENT '会话主题',
    status            VARCHAR(32)  NOT NULL COMMENT '状态：WAITING-等待管理员认领，ACTIVE-沟通中，CLOSED-已关闭',
    created_by        BIGINT       NOT NULL COMMENT '发起会话的用户主键',
    last_message_id   BIGINT                DEFAULT NULL COMMENT '最后一条消息主键，用于会话列表排序和摘要查询',
    last_message_at   DATETIME              DEFAULT NULL COMMENT '最后一条消息发送时间',
    closed_by         BIGINT                DEFAULT NULL COMMENT '关闭会话的用户主键',
    close_reason      VARCHAR(500)           DEFAULT NULL COMMENT '关闭原因',
    closed_at         DATETIME              DEFAULT NULL COMMENT '关闭时间',
    version           INT          NOT NULL DEFAULT 0 COMMENT '会话状态变更使用的乐观锁版本号',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                             ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_conversation_task (conversation_type, task_id),
    KEY idx_chat_conversation_status_updated (status, updated_at),
    KEY idx_chat_conversation_type_status_message
        (conversation_type, status, last_message_at),
    KEY idx_chat_conversation_creator_created (created_by, created_at),
    CONSTRAINT chk_chat_conversation_type
        CHECK (conversation_type IN ('TASK_SERVICE', 'ADMIN_SUPPORT')),
    CONSTRAINT chk_chat_conversation_status
        CHECK (status IN ('WAITING', 'ACTIVE', 'CLOSED')),
    CONSTRAINT chk_chat_conversation_creator
        CHECK (created_by > 0),
    CONSTRAINT chk_chat_conversation_version
        CHECK (version >= 0),
    CONSTRAINT chk_chat_conversation_business
        CHECK (
            (
                conversation_type = 'TASK_SERVICE'
                AND task_id IS NOT NULL
                AND task_id > 0
                AND status IN ('ACTIVE', 'CLOSED')
            )
            OR (
                conversation_type = 'ADMIN_SUPPORT'
                AND task_id IS NULL
            )
        ),
    CONSTRAINT chk_chat_conversation_subject
        CHECK (CHAR_LENGTH(TRIM(subject)) > 0),
    CONSTRAINT chk_chat_conversation_last_message
        CHECK (
            (last_message_id IS NULL AND last_message_at IS NULL)
            OR (
                last_message_id IS NOT NULL
                AND last_message_id > 0
                AND last_message_at IS NOT NULL
            )
        ),
    CONSTRAINT chk_chat_conversation_close
        CHECK (
            (
                status = 'CLOSED'
                AND closed_by IS NOT NULL
                AND closed_by > 0
                AND closed_at IS NOT NULL
                AND closed_at >= created_at
            )
            OR (
                status <> 'CLOSED'
                AND closed_by IS NULL
                AND close_reason IS NULL
                AND closed_at IS NULL
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '居民、抄表员和管理员之间的聊天会话表';

CREATE TABLE chat_conversation_member
(
    conversation_id     BIGINT      NOT NULL COMMENT '聊天会话主键',
    user_id             BIGINT      NOT NULL COMMENT '参与会话的用户主键',
    member_role         VARCHAR(32) NOT NULL COMMENT '成员角色：RESIDENT、METER_READER、ADMIN',
    last_read_message_id BIGINT              DEFAULT NULL COMMENT '该成员最后读取的消息主键',
    last_read_at        DATETIME             DEFAULT NULL COMMENT '最后读取时间',
    joined_at           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入会话时间',
    left_at             DATETIME             DEFAULT NULL COMMENT '退出或被转接离开会话的时间',
    PRIMARY KEY (conversation_id, user_id),
    KEY idx_chat_member_user_active (user_id, left_at, conversation_id),
    KEY idx_chat_member_conversation_role
        (conversation_id, member_role, left_at),
    CONSTRAINT chk_chat_member_conversation_id
        CHECK (conversation_id > 0),
    CONSTRAINT chk_chat_member_user_id
        CHECK (user_id > 0),
    CONSTRAINT chk_chat_member_role
        CHECK (member_role IN ('RESIDENT', 'METER_READER', 'ADMIN')),
    CONSTRAINT chk_chat_member_read_cursor
        CHECK (
            (last_read_message_id IS NULL AND last_read_at IS NULL)
            OR (
                last_read_message_id IS NOT NULL
                AND last_read_message_id > 0
                AND last_read_at IS NOT NULL
            )
        ),
    CONSTRAINT chk_chat_member_time_order
        CHECK (left_at IS NULL OR left_at >= joined_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '聊天会话参与成员及其已读游标表';

CREATE TABLE chat_message
(
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '聊天消息主键',
    conversation_id   BIGINT        NOT NULL COMMENT '所属聊天会话主键',
    client_message_id VARCHAR(64)   NOT NULL COMMENT '客户端消息幂等编号',
    sender_id         BIGINT                 DEFAULT NULL COMMENT '发送人用户主键；系统消息为空',
    sender_role       VARCHAR(32)   NOT NULL COMMENT '发送角色：RESIDENT、METER_READER、ADMIN、SYSTEM',
    message_type      VARCHAR(32)   NOT NULL COMMENT '消息类型：TEXT-文本，SYSTEM-系统消息',
    content           VARCHAR(2000) NOT NULL COMMENT '消息正文',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常，1-已删除',
    deleted_by        BIGINT                 DEFAULT NULL COMMENT '删除消息的用户主键',
    deleted_at        DATETIME               DEFAULT NULL COMMENT '删除时间',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_message_client (conversation_id, client_message_id),
    KEY idx_chat_message_conversation_cursor (conversation_id, id),
    KEY idx_chat_message_sender_created (sender_id, created_at),
    CONSTRAINT chk_chat_message_conversation_id
        CHECK (conversation_id > 0),
    CONSTRAINT chk_chat_message_sender_role
        CHECK (sender_role IN ('RESIDENT', 'METER_READER', 'ADMIN', 'SYSTEM')),
    CONSTRAINT chk_chat_message_type
        CHECK (message_type IN ('TEXT', 'SYSTEM')),
    CONSTRAINT chk_chat_message_content
        CHECK (CHAR_LENGTH(TRIM(content)) > 0),
    CONSTRAINT chk_chat_message_sender
        CHECK (
            (
                message_type = 'SYSTEM'
                AND sender_role = 'SYSTEM'
                AND sender_id IS NULL
            )
            OR (
                message_type = 'TEXT'
                AND sender_role IN ('RESIDENT', 'METER_READER', 'ADMIN')
                AND sender_id IS NOT NULL
                AND sender_id > 0
            )
        ),
    CONSTRAINT chk_chat_message_deleted
        CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_chat_message_delete_operation
        CHECK (
            (
                deleted = 0
                AND deleted_by IS NULL
                AND deleted_at IS NULL
            )
            OR (
                deleted = 1
                AND deleted_by IS NOT NULL
                AND deleted_by > 0
                AND deleted_at IS NOT NULL
                AND deleted_at >= created_at
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '聊天消息持久化表';
