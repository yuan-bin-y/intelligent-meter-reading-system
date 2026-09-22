package com.byy.meterreading.trace;

import com.byy.meterreading.common.trace.TraceIdContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 为每次定时任务执行创建独立 traceId。
 *
 * <p>定时任务没有 HTTP 上游请求，必须自行建立追踪上下文；执行结束后
 * 自动恢复或清理，防止调度线程复用时串用上一次任务的 traceId。</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScheduledTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(
            ScheduledTraceAspect.class
    );

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object traceScheduledExecution(
            ProceedingJoinPoint joinPoint
    ) throws Throwable {
        try (TraceIdContext.Scope ignored = TraceIdContext.openNew()) {
            log.debug("开始执行定时任务：{}", joinPoint.getSignature());
            return joinPoint.proceed();
        }
    }
}
