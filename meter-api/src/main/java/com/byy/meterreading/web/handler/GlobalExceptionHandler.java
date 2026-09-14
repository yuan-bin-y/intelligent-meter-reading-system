package com.byy.meterreading.web.handler;

import com.byy.meterreading.common.result.ApiErrorCode;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.common.trace.TraceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一处理进入 Controller 调用链后的异常。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 用户名、密码或账号状态认证失败时，统一返回认证失败。
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationException(
            AuthenticationException exception
    ) {
        return Result.failure(
                ApiErrorCode.AUTH_FAILED,
                ApiErrorCode.AUTH_FAILED.getMessage()
        );
    }

    /**
     * 将 LoginDTO 等请求对象的字段校验错误整理成字段错误映射。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "参数不正确"
                                : error.getDefaultMessage()
                )
        );

        return Result.validation(fieldErrors);
    }

    /**
     * 记录未预期异常的 traceId，并向客户端隐藏内部异常细节。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception exception) {
        String traceId = TraceIdContext.getOrCreate();
        log.error("服务器内部错误，traceId={}", traceId, exception);

        return Result.failure(
                ApiErrorCode.INTERNAL_ERROR,
                ApiErrorCode.INTERNAL_ERROR.getMessage()
        );
    }
}
