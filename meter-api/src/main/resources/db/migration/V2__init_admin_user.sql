-- 初始化本地登录测试使用的管理员账号，密码字段只保存 BCrypt 哈希。
INSERT INTO sys_user (
    username,
    password_hash,
    display_name,
    status,
    created_at,
    updated_at
)
VALUES (
    'admin',
    '$2a$10$MHxzl0O/teobTPbgaSSeV.kIMxuO2AuvxbyQ4w/SV0YXzA7yPGWNK',
    '系统管理员',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 通过角色编码查找 ADMIN，避免依赖固定的角色主键。
INSERT INTO sys_user_role (user_id, role_id, created_at)
SELECT sys_user.id, sys_role.id, CURRENT_TIMESTAMP
FROM sys_user
INNER JOIN sys_role
        ON sys_role.role_code = 'ADMIN'
WHERE sys_user.username = 'admin';
