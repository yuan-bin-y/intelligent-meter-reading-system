package com.byy.meterreading.cache;

import com.byy.meterreading.common.exception.DatabaseBusyException;
import com.byy.meterreading.common.trace.TraceIdContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** 同一Key共享一次加载；不同Key共用有上限的执行器，无排队和无限回源。 */
@Component
public class DatabaseLoadGate {
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;
    private final long timeoutMillis;
    private final ThreadLocal<Long> deadline = new ThreadLocal<>();
    private final Counter rejected;
    private final Counter timedOut;
    private final Counter coalesced;

    public DatabaseLoadGate(BusinessCacheProperties properties, MeterRegistry registry) {
        timeoutMillis = properties.getLoadTimeout().toMillis();
        int concurrency = properties.getLoadConcurrency();
        executor = new ThreadPoolExecutor(concurrency, concurrency, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "business-cache-load");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        rejected = registry.counter("business.cache.load.rejected");
        timedOut = registry.counter("business.cache.load.timeout");
        coalesced = registry.counter("business.cache.load.coalesced");
        Gauge.builder("business.cache.load.active", executor, ThreadPoolExecutor::getActiveCount).register(registry);
        Gauge.builder("business.cache.load.inflight", inFlight, ConcurrentHashMap::size).register(registry);
    }

    @SuppressWarnings("unchecked")
    public <T> T load(String key, Supplier<T> loader) {
        var candidate = new CompletableFuture<Object>();
        var shared = inFlight.putIfAbsent(key, candidate);
        if (shared == null) {
            shared = candidate;
            String traceId = TraceIdContext.getOrCreate();
            try {
                executor.execute(() -> {
                    deadline.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis));
                    try (var ignored = TraceIdContext.open(traceId)) {
                        candidate.complete(loader.get());
                    } catch (Throwable failure) {
                        candidate.completeExceptionally(failure);
                    } finally {
                        deadline.remove();
                        inFlight.remove(key, candidate);
                    }
                });
            } catch (RejectedExecutionException exception) {
                rejected.increment();
                candidate.completeExceptionally(new DatabaseBusyException("系统繁忙，请稍后重试"));
                inFlight.remove(key, candidate);
            }
        } else {
            coalesced.increment();
        }
        try {
            return (T) shared.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            timedOut.increment();
            // 不释放仍在执行SQL的槽位。即使驱动不响应中断，也无法启动额外SQL突破上限。
            throw new DatabaseBusyException("查询超时，请稍后重试");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DatabaseBusyException("请求已中断，请稍后重试");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("数据库加载失败", cause);
        }
    }

    public boolean mayPublish() {
        Long limit = deadline.get();
        return limit != null && System.nanoTime() < limit;
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
