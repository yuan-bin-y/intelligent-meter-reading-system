package com.byy.meterreading.common.result;

import lombok.Getter;

/**
 * V1 API 统一错误码。
 */
@Getter
public enum ApiErrorCode {

    BAD_REQUEST("请求参数错误"),
    AUTH_REQUIRED("请先登录"),
    AUTH_FAILED("用户名或密码错误"),
    FORBIDDEN("无权限访问"),
    RESOURCE_NOT_FOUND("资源不存在"),
    VERSION_CONFLICT("数据版本冲突"),
    RESOURCE_CONFLICT("资源冲突"),
    PAYLOAD_TOO_LARGE("上传文件过大"),
    UNSUPPORTED_MEDIA_TYPE("不支持的媒体类型"),
    RATE_LIMITED("请求过于频繁"),
    UPSTREAM_ERROR("上游服务暂时不可用"),
    INTERNAL_ERROR("服务器内部错误");

    /**
     * 对应的中文错误信息。
     */
    private final String message;

    ApiErrorCode(String message) {
        this.message = message;
    }

    /**
     * API 对外使用的稳定错误编码。
     */
    public String code() {
        return name();
    }
}
