package com.byy.meterreading.common.trace;

import java.util.UUID;

/**
 * 保存当前线程的 traceId。
 */
public final class TraceIdContext {

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    private TraceIdContext() {
    }

    public static String getOrCreate() {
        String traceId = TRACE_ID_HOLDER.get();
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            TRACE_ID_HOLDER.set(traceId);
        }
        return traceId;
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
    }
}
