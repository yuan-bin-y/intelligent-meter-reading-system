package com.byy.meterreading.common.exception;

/**
 * 客户端提交的数据版本已经落后于数据库当前版本。
 */
public class VersionConflictException extends RuntimeException {

    public VersionConflictException(String message) {
        super(message);
    }
}
