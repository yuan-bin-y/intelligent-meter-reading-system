package com.byy.meterreading.common.trace;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 统一维护当前线程的 traceId，并同步写入 SLF4J MDC。
 *
 * <p>HTTP 响应、运行日志和跨线程消息都通过本类读写，避免同时维护
 * ThreadLocal 与 MDC 时出现两个不同的追踪标识。</p>
 */
public final class TraceIdContext {

    public static final String MDC_KEY = "traceId";
    public static final String HTTP_HEADER = "X-Trace-Id";
    public static final String MESSAGE_HEADER = "x-trace-id";

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{32}$"
    );

    private TraceIdContext() {
    }

    /** 返回当前线程已有的 traceId；没有时返回 null。 */
    public static String get() {
        return MDC.get(MDC_KEY);
    }

    /** 返回当前 traceId；当前线程尚未建立上下文时创建一个新的。 */
    public static String getOrCreate() {
        String traceId = get();
        if (!isValid(traceId)) {
            traceId = create();
        }
        return traceId;
    }

    /** 创建新的 traceId，并替换当前线程已有的追踪上下文。 */
    public static String create() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(MDC_KEY, traceId);
        return traceId;
    }

    /** 使用合法的上游 traceId；缺失或格式非法时由本服务创建新的。 */
    public static String setOrCreate(String traceId) {
        if (!isValid(traceId)) {
            return create();
        }
        String normalized = traceId.toLowerCase();
        MDC.put(MDC_KEY, normalized);
        return normalized;
    }

    /**
     * 临时切换追踪上下文，Scope 关闭时自动恢复进入前的 traceId。
     * 适用于线程池、消息监听和批量任务逐条处理。
     */
    public static Scope open(String traceId) {
        String previousTraceId = get();
        setOrCreate(traceId);
        return new Scope(previousTraceId);
    }

    /** 为一个没有上游请求的独立任务建立全新的追踪上下文。 */
    public static Scope openNew() {
        String previousTraceId = get();
        create();
        return new Scope(previousTraceId);
    }

    /** 只接受服务内部使用的 32 位十六进制 traceId，防止日志注入。 */
    public static boolean isValid(String traceId) {
        return traceId != null
                && TRACE_ID_PATTERN.matcher(traceId).matches();
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /** try-with-resources 使用的上下文作用域。 */
    public static final class Scope implements AutoCloseable {

        private final String previousTraceId;
        private boolean closed;

        private Scope(String previousTraceId) {
            this.previousTraceId = previousTraceId;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (isValid(previousTraceId)) {
                MDC.put(MDC_KEY, previousTraceId);
            } else {
                clear();
            }
        }
    }
}
