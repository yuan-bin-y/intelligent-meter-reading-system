package com.byy.meterreading.cache;

import com.byy.meterreading.common.exception.DatabaseBusyException;
import com.byy.meterreading.maintenance.RedisMaintenanceQueue;
import com.byy.meterreading.support.IntegrationInfrastructure;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "METER_IT", matches = "true")
class DatabaseFallbackIntegrationTest {
    @Test
    void redisOutageBurstCannotExceedFourConcurrentDatabaseLoads() throws Exception {
        var infra = new IntegrationInfrastructure();
        var properties = new BusinessCacheProperties();
        properties.setLoadConcurrency(4);
        properties.setLoadTimeout(Duration.ofSeconds(3));
        var registry = new SimpleMeterRegistry();
        var gate = new DatabaseLoadGate(properties, registry);
        var unavailable = mock(StringRedisTemplate.class);
        when(unavailable.opsForValue()).thenThrow(new IllegalStateException("injected cache outage"));
        var cache = new BusinessCacheService(unavailable, mock(RedissonClient.class), new ObjectMapper(),
                properties, new CacheWriteGuard(unavailable), new RedisMaintenanceQueue(infra.jdbc), gate);
        var clients = Executors.newFixedThreadPool(32);
        var start = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var successful = new AtomicInteger();
        var rejected = new AtomicInteger();
        try {
            var calls = new ArrayList<Future<?>>();
            for (long id = 0; id < 256; id++) {
                long key = id;
                calls.add(clients.submit(() -> {
                    try {
                        start.await();
                        cache.getDeviceDetail(key, () -> {
                            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                            try {
                                // 只用于注入慢SQL，验证并发保护，不把SLEEP当作业务吞吐压测。
                                infra.jdbc.execute("SELECT SLEEP(0.05)");
                                return null;
                            } finally {
                                active.decrementAndGet();
                            }
                        });
                        successful.incrementAndGet();
                    } catch (DatabaseBusyException expected) {
                        rejected.incrementAndGet();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }));
            }
            start.countDown();
            for (var call : calls) {
                call.get(10, TimeUnit.SECONDS);
            }
            assertTrue(maximum.get() <= 4);
            assertTrue(successful.get() > 0);
            assertTrue(rejected.get() > 0);
            assertTrue(successful.get() + rejected.get() == 256);
            System.out.printf("CACHE_OUTAGE requests=256 peakDatabaseLoads=%d succeeded=%d rejected=%d%n",
                    maximum.get(), successful.get(), rejected.get());
        } finally {
            clients.shutdownNow();
            gate.close();
            infra.close();
        }
    }
}
