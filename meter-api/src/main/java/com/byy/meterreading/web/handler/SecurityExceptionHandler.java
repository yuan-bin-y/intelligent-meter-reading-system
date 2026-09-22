package com.byy.meterreading.web.handler;

import com.byy.meterreading.common.result.ApiErrorCode;
import com.byy.meterreading.common.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 统一处理 Spring Security 过滤器链中的 401 和 403 异常。
 */
@Component
public class SecurityExceptionHandler implements
        AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(
            SecurityExceptionHandler.class
    );

    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 请求未携带有效身份信息时返回 401。
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        logSecurityException(
                request,
                HttpStatus.UNAUTHORIZED,
                exception
        );
        writeResponse(
                response,
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTH_REQUIRED
        );
    }

    /**
     * 当前用户已经认证但没有访问权限时返回 403。
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        logSecurityException(
                request,
                HttpStatus.FORBIDDEN,
                exception
        );
        writeResponse(
                response,
                HttpStatus.FORBIDDEN,
                ApiErrorCode.FORBIDDEN
        );
    }

    /**
     * 将安全异常转换成项目统一响应结构。
     */
    private void writeResponse(
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorCode errorCode
    ) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(
                response.getOutputStream(),
                Result.failure(errorCode, errorCode.getMessage())
        );
    }

    /**
     * Security 过滤器异常不会进入 ControllerAdvice，因此在安全边界统一记录。
     */
    private void logSecurityException(
            HttpServletRequest request,
            HttpStatus status,
            Exception exception
    ) {
        log.warn(
                "安全访问异常：method={}, path={}, status={}, exception={}",
                request.getMethod(),
                request.getRequestURI(),
                status.value(),
                exception.getClass().getSimpleName()
        );
    }
}
