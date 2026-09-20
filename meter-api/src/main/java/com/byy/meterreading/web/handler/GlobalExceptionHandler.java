package com.byy.meterreading.web.handler;

import com.byy.meterreading.auth.exception.RateLimitExceededException;
import com.byy.meterreading.common.exception.ObjectStorageException;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.common.result.ApiErrorCode;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.common.trace.TraceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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
     * 处理 @PreAuthorize 等方法级权限校验失败。
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAuthorizationDeniedException(
            AuthorizationDeniedException exception
    ) {
        return Result.failure(
                ApiErrorCode.FORBIDDEN,
                ApiErrorCode.FORBIDDEN.getMessage()
        );
    }

    /**
     * 管理端查询的用户等业务资源不存在时返回 404。
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleResourceNotFoundException(
            ResourceNotFoundException exception
    ) {
        return Result.failure(
                ApiErrorCode.RESOURCE_NOT_FOUND,
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
     * 请求体包含非法枚举、错误日期、字段类型不匹配或无效 JSON 时返回 400。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return Result.failure(
                ApiErrorCode.BAD_REQUEST,
                "请求体格式不正确"
        );
    }

    /**
     * 查询参数缺失，或路径、查询参数无法转换成目标类型时返回 400。
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleRequestParameterException(Exception exception) {
        return Result.failure(
                ApiErrorCode.BAD_REQUEST,
                "请求参数不正确"
        );
    }

    /** multipart 请求在进入 Controller 前超过上传上限时返回 413。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<Void> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception
    ) {
        return Result.failure(
                ApiErrorCode.PAYLOAD_TOO_LARGE,
                ApiErrorCode.PAYLOAD_TOO_LARGE.getMessage()
        );
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
     * 已转换成安全业务信息的资源唯一性冲突返回 409。
     */
    @ExceptionHandler(ResourceConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleResourceConflictException(
            ResourceConflictException exception
    ) {
        return Result.failure(
                ApiErrorCode.RESOURCE_CONFLICT,
                exception.getMessage()
        );
    }

    /**
     * 乐观锁版本已经过期时提示客户端刷新后重试。
     */
    @ExceptionHandler(VersionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleVersionConflictException(
            VersionConflictException exception
    ) {
        return Result.failure(
                ApiErrorCode.VERSION_CONFLICT,
                exception.getMessage()
        );
    }

    /**
     * 未被业务层转换的数据库唯一索引异常只返回通用提示，避免暴露 SQL 细节。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDuplicateKeyException(
            DuplicateKeyException exception
    ) {
        return Result.failure(
                ApiErrorCode.RESOURCE_CONFLICT,
                ApiErrorCode.RESOURCE_CONFLICT.getMessage()
        );
    }

    /** 阿里云 OSS 不可用时返回明确的上游服务错误。 */
    @ExceptionHandler(ObjectStorageException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> handleObjectStorageException(
            ObjectStorageException exception
    ) {
        String traceId = TraceIdContext.getOrCreate();
        log.error("OSS 调用失败，traceId={}", traceId, exception);
        return Result.failure(
                ApiErrorCode.UPSTREAM_ERROR,
                exception.getMessage()
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
