package com.byy.meterreading.common.result;

import com.byy.meterreading.common.trace.TraceIdContext;

import java.util.Map;

/**
 * V1 API 统一响应外壳，成功与失败共用同一个结构。
 *
 * <p>成功时 code 固定为 OK；失败时 code 取 ApiErrorCode 名称。
 * 失败时 data 为 null，参数校验失败时 data 携带字段级错误信息。</p>
 *
 * @param code    业务状态码：成功 OK，失败为 ApiErrorCode 名称
 * @param message 人类可读的说明
 * @param data    业务数据；失败时为 null，校验失败时为字段错误映射
 * @param traceId 本次响应的追踪标识
 */
public record Result<T>(
        String code,
        String message,
        T data,
        String traceId
) {

    public static final String SUCCESS_CODE = "OK";
    public static final String SUCCESS_MESSAGE = "success";

    public static <T> Result<T> success(T data) {
        return new Result<>(
                SUCCESS_CODE,
                SUCCESS_MESSAGE,
                data,
                TraceIdContext.getOrCreate()
        );
    }

    public static Result<Void> failure(ApiErrorCode code, String message) {
        return new Result<>(
                code.code(),
                message,
                null,
                TraceIdContext.getOrCreate()
        );
    }

    public static Result<Map<String, String>> validation(
            Map<String, String> fieldErrors
    ) {
        return new Result<>(
                ApiErrorCode.BAD_REQUEST.code(),
                "请求参数不正确",
                Map.copyOf(fieldErrors),
                TraceIdContext.getOrCreate()
        );
    }
}
