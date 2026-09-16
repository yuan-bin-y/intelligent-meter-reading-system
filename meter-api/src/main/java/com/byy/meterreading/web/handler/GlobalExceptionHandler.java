package com.byy.meterreading.web.handler;

import com.byy.meterreading.auth.exception.RateLimitExceededException;
import com.byy.meterreading.common.result.ApiErrorCode;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.common.trace.TraceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
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
     * JWT 缺少用户标识，或对应用户已经不存在、被禁用时返回登录失效。
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationCredentialsNotFoundException(
            AuthenticationCredentialsNotFoundException exception
    ) {
        return Result.failure(
                ApiErrorCode.AUTH_REQUIRED,
                ApiErrorCode.AUTH_REQUIRED.getMessage()
        );
    }

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
     * 登录、注册请求超过频率限制，或登录失败次数达到锁定阈值时返回 429。
     */
    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Void> handleRateLimitExceededException(
            RateLimitExceededException exception
    ) {
        return Result.failure(
                ApiErrorCode.RATE_LIMITED,
                exception.getMessage()
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
     * 处理已经通过字段校验，但不满足业务规则的请求参数。
     * 例如原密码错误，或者新密码与原密码相同。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(
            IllegalArgumentException exception
    ) {
        return Result.failure(
                ApiErrorCode.BAD_REQUEST,
                exception.getMessage()
        );
    }

    /**
     * 数据库唯一索引冲突时返回资源冲突，注册场景中表示用户名已经存在。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDuplicateKeyException(
            DuplicateKeyException exception
    ) {
        return Result.failure(
                ApiErrorCode.RESOURCE_CONFLICT,
                "用户名已存在"
        );
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
