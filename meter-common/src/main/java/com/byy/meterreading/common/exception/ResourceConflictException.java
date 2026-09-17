package com.byy.meterreading.common.exception;

/**
 * 创建或修改业务资源时发生唯一性冲突。
 */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }

    public ResourceConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
