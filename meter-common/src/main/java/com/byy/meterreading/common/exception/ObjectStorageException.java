package com.byy.meterreading.common.exception;

/**
 * 阿里云 OSS 未配置或调用失败时抛出的统一上游存储异常。
 */
public class ObjectStorageException extends RuntimeException {

    public ObjectStorageException(String message) {
        super(message);
    }

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
