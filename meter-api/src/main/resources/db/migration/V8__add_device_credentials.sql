ALTER TABLE device
    ADD COLUMN secret_hash CHAR(64)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NULL COMMENT '设备密钥SHA-256摘要'
        AFTER version,
    ADD COLUMN credential_version INT NOT NULL DEFAULT 0
        COMMENT '设备凭证版本：0-尚未配置，正整数-当前密钥版本'
        AFTER secret_hash,
    ADD COLUMN secret_rotated_at DATETIME NULL
        COMMENT '设备密钥最近生成或重置时间'
        AFTER credential_version,
    ADD CONSTRAINT chk_device_credential_version
        CHECK (credential_version >= 0),
    ADD CONSTRAINT chk_device_credential_consistency
        CHECK (
            (
                secret_hash IS NULL
                AND credential_version = 0
                AND secret_rotated_at IS NULL
            )
            OR
            (
                secret_hash IS NOT NULL
                AND credential_version >= 1
                AND secret_rotated_at IS NOT NULL
            )
        );
