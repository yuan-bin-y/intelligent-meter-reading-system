package com.byy.meterreading.auth.exception;

/**
 * 登录、注册请求超过频率限制，或者登录失败次数达到锁定阈值。
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
