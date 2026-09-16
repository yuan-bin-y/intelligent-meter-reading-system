package com.byy.meterreading.common.exception;

/**
 * 请求的业务资源不存在。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
