package com.byy.meterreading.cache;

import com.byy.meterreading.common.exception.DatabaseBusyException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseLoadGateTest {
    @Test
    void concurrentRequestsForOneKeyShareOneQuery() throws Exception {
        var registry = new SimpleMeterRegistry();
        var gate = new DatabaseLoadGate(new BusinessCacheProperties(), registry);
        var clients = Executors.newFixedThreadPool(20);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queries = new AtomicInteger();
        try {
            var first = clients.submit(() -> gate.load("hot", () -> {
                queries.incrementAndGet();
                started.countDown();
                await(release);
                return "result";
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var others = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 19; i++) {
                others.add(clients.submit(() -> gate.load("hot", () -> {
                    queries.incrementAndGet();
                    return "unexpected";
                })));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (registry.counter("business.cache.load.coalesced").count() < 19 && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertEquals(19, registry.counter("business.cache.load.coalesced").count());
            release.countDown();
            assertEquals("result", first.get(2, TimeUnit.SECONDS));
            for (var result : others) {
                assertEquals("result", result.get(2, TimeUnit.SECONDS));
            }
            assertEquals(1, queries.get());
        } finally {
            release.countDown();
            clients.shutdownNow();
            gate.close();
        }
    }

    @Test
    void timedOutSqlKeepsItsSlotUntilItActuallyStops() throws Exception {
        var properties = new BusinessCacheProperties();
        properties.setLoadConcurrency(1);
        properties.setLoadTimeout(Duration.ofMillis(100));
        var registry = new SimpleMeterRegistry();
        var gate = new DatabaseLoadGate(properties, registry);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        try {
            assertThrows(DatabaseBusyException.class, () -> gate.load("slow", () -> {
                await(release); // 模拟不响应请求线程超时的JDBC驱动。
                assertFalse(gate.mayPublish());
                finished.countDown();
                return "late";
            }));
            assertThrows(DatabaseBusyException.class, () -> gate.load("another", () -> "must not run"));
            assertEquals(1, registry.counter("business.cache.load.rejected").count());
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            gate.close();
        }
    }

    @Test
    void databaseFailureIsPropagatedWithoutRetryingTheQuery() {
        var gate = new DatabaseLoadGate(new BusinessCacheProperties(), new SimpleMeterRegistry());
        var calls = new AtomicInteger();
        try {
            assertThrows(IllegalArgumentException.class, () -> gate.load("broken", () -> {
                calls.incrementAndGet();
                throw new IllegalArgumentException("SQL failed");
            }));
            assertEquals(1, calls.get());
        } finally {
            gate.close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
