package com.byy.meterreading.audit.aspect;

import com.byy.meterreading.common.audit.OperationAudit;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.common.trace.TraceIdContext;
import com.byy.meterreading.model.OperationLog;
import com.byy.meterreading.model.enums.OperationResult;
import com.byy.meterreading.service.OperationLogService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 拦截带 {@link OperationAudit} 的 Controller 方法并保存操作审计日志。
 * 审计采集、序列化或数据库写入失败时只记录运行日志，不改变原业务结果。
 */
@Aspect
@Component
public class OperationAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(
            OperationAuditAspect.class
    );

    private static final int MAX_PARAMS_LENGTH = 4000;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String MASKED_VALUE = "******";

    /** 匹配 JSON 中常见的密码、Token、密钥和认证凭证字段。 */
    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "(?i)(\\\"[^\\\"]*(?:password|token|secret|authorization|credential)[^\\\"]*\\\"\\s*:\\s*)"
                    + "(\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|[^,}\\]]+)"
    );

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final ExpressionParser expressionParser =
            new SpelExpressionParser();

    public OperationAuditAspect(
            OperationLogService operationLogService,
            ObjectMapper objectMapper
    ) {
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 先执行原业务，再根据返回值或异常生成 SUCCESS/FAILED 日志。
     * finally 保证业务成功和失败两条路径都会尝试记录审计信息。
     */
    @Around("@annotation(operationAudit)")
    public Object recordOperation(
            ProceedingJoinPoint joinPoint,
            OperationAudit operationAudit
    ) throws Throwable {
        long startedAt = System.nanoTime();
        Object returnValue = null;
        Throwable businessException = null;

        try {
            returnValue = joinPoint.proceed();
            return returnValue;
        } catch (Throwable exception) {
            businessException = exception;
            throw exception;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );
            saveAuditSafely(
                    joinPoint,
                    operationAudit,
                    returnValue,
                    businessException,
                    durationMs
            );
        }
    }

    /** 审计功能出现任何异常时只输出 SLF4J 日志，不阻断主营业务。 */
    private void saveAuditSafely(
            ProceedingJoinPoint joinPoint,
            OperationAudit operationAudit,
            Object returnValue,
            Throwable businessException,
            long durationMs
    ) {
        try {
            OperationLog operationLog = buildOperationLog(
                    joinPoint,
                    operationAudit,
                    returnValue,
                    businessException,
                    durationMs
            );
            operationLogService.saveLog(operationLog);
        } catch (Exception auditException) {
            log.error(
                    "保存操作审计日志失败，module={}，action={}",
                    operationAudit.module(),
                    operationAudit.action(),
                    auditException
            );
        }
    }

    /** 将注解中的固定信息与当前请求产生的动态信息组装成持久化实体。 */
    private OperationLog buildOperationLog(
            ProceedingJoinPoint joinPoint,
            OperationAudit operationAudit,
            Object returnValue,
            Throwable businessException,
            long durationMs
    ) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        OperationLog operationLog = OperationLog.builder()
                .traceId(TraceIdContext.getOrCreate())
                .module(operationAudit.module())
                .action(operationAudit.action())
                .resourceType(trimToNull(operationAudit.resourceType()))
                .resourceId(resolveResourceIdSafely(
                        joinPoint,
                        signature,
                        operationAudit.resourceIdExpression(),
                        returnValue
                ))
                .requestParams(operationAudit.recordParams()
                        ? serializeParameters(joinPoint, signature)
                        : null)
                .result(businessException == null
                        ? OperationResult.SUCCESS.name()
                        : OperationResult.FAILED.name())
                .errorMessage(safeErrorMessage(businessException))
                .durationMs(durationMs)
                .createdAt(LocalDateTime.now())
                .build();

        fillCurrentUser(operationLog);
        fillRequestInformation(operationLog, signature);
        return operationLog;
    }

    /**
     * 资源 ID 只是审计附加信息，解析失败不能导致整条审计日志丢失。
     *
     * <p>例如新增接口使用 {@code #result.data.meterId}，业务校验失败时
     * Controller 没有返回值，此时 {@code result} 为 {@code null}。失败日志
     * 仍应保存，只将无法取得的资源 ID 留空。</p>
     */
    private Long resolveResourceIdSafely(
            ProceedingJoinPoint joinPoint,
            MethodSignature signature,
            String expression,
            Object returnValue
    ) {
        try {
            return resolveResourceId(
                    joinPoint,
                    signature,
                    expression,
                    returnValue
            );
        } catch (RuntimeException exception) {
            log.debug(
                    "无法解析审计资源ID，expression={}，method={}",
                    expression,
                    signature.toShortString()
            );
            return null;
        }
    }

    /** 从 Spring Security 的 Jwt Principal 中读取操作人，不信任前端传入的用户字段。 */
    private void fillCurrentUser(OperationLog operationLog) {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            operationLog.setOperatorUsername("SYSTEM");
            return;
        }

        Object userIdClaim = jwt.getClaim("userId");
        if (userIdClaim instanceof Number userId) {
            operationLog.setOperatorId(userId.longValue());
        }
        operationLog.setOperatorUsername(jwt.getSubject());
    }

    /** 获取请求方法、路径和客户端 IP；非 HTTP 调用使用安全的内部调用占位值。 */
    private void fillRequestInformation(
            OperationLog operationLog,
            MethodSignature signature
    ) {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            operationLog.setRequestMethod(request.getMethod());
            operationLog.setRequestPath(request.getRequestURI());
            operationLog.setClientIp(resolveClientIp(request));
            return;
        }

        operationLog.setRequestMethod("INTERNAL");
        operationLog.setRequestPath(
                signature.getDeclaringTypeName()
                        + "#"
                        + signature.getName()
        );
    }

    /**
     * 解析 #deviceId 或 #result.data.deviceId 等表达式；表达式为空时不记录资源 ID。
     */
    private Long resolveResourceId(
            ProceedingJoinPoint joinPoint,
            MethodSignature signature,
            String expression,
            Object returnValue
    ) {
        String normalizedExpression = trimToNull(expression);
        if (normalizedExpression == null) {
            return null;
        }

        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = signature.getParameterNames();
        Object[] arguments = joinPoint.getArgs();
        if (parameterNames != null) {
            for (int index = 0;
                    index < parameterNames.length && index < arguments.length;
                    index++) {
                context.setVariable(parameterNames[index], arguments[index]);
            }
        }
        context.setVariable("result", returnValue);

        Object value = expressionParser
                .parseExpression(normalizedExpression)
                .getValue(context);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue
                && !stringValue.isBlank()) {
            return Long.valueOf(stringValue);
        }
        throw new IllegalArgumentException(
                "审计资源ID表达式结果必须是数字"
        );
    }

    /** 将方法参数整理为 JSON，并跳过框架对象、文件内容和认证对象。 */
    private String serializeParameters(
            ProceedingJoinPoint joinPoint,
            MethodSignature signature
    ) {
        String[] parameterNames = signature.getParameterNames();
        Object[] arguments = joinPoint.getArgs();
        Map<String, Object> serializableParameters = new LinkedHashMap<>();

        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            if (shouldSkipArgument(argument)) {
                continue;
            }

            String parameterName = parameterNames != null
                    && index < parameterNames.length
                    ? parameterNames[index]
                    : "arg" + index;
            serializableParameters.put(
                    parameterName,
                    isSensitiveName(parameterName)
                            ? MASKED_VALUE
                            : argument
            );
        }

        if (serializableParameters.isEmpty()) {
            return null;
        }

        String json = objectMapper.writeValueAsString(
                serializableParameters
        );
        String maskedJson = SENSITIVE_JSON_FIELD.matcher(json)
                .replaceAll("$1\"" + MASKED_VALUE + "\"");
        return truncate(maskedJson, MAX_PARAMS_LENGTH);
    }

    private boolean shouldSkipArgument(Object argument) {
        return argument instanceof ServletRequest
                || argument instanceof ServletResponse
                || argument instanceof MultipartFile
                || argument instanceof BindingResult
                || argument instanceof Authentication
                || argument instanceof Jwt
                || argument instanceof InputStream
                || argument instanceof OutputStream
                || argument instanceof byte[];
    }

    private boolean isSensitiveName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("authorization")
                || normalized.contains("credential");
    }

    /** 仅保存可安全展示的业务错误；未知异常的完整堆栈由全局处理器写入运行日志。 */
    private String safeErrorMessage(Throwable exception) {
        if (exception == null) {
            return null;
        }
        if (exception instanceof IllegalArgumentException
                || exception instanceof ResourceNotFoundException
                || exception instanceof ResourceConflictException
                || exception instanceof VersionConflictException) {
            String message = trimToNull(exception.getMessage());
            return message == null
                    ? "业务操作失败"
                    : truncate(message, MAX_ERROR_LENGTH);
        }
        return "服务器内部错误";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = trimToNull(
                request.getHeader("X-Forwarded-For")
        );
        if (forwardedFor != null) {
            int separatorIndex = forwardedFor.indexOf(',');
            return separatorIndex < 0
                    ? forwardedFor
                    : forwardedFor.substring(0, separatorIndex).trim();
        }

        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        return realIp == null ? request.getRemoteAddr() : realIp;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
