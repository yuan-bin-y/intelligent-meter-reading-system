package com.byy.meterreading.cache;

import com.byy.meterreading.support.IntegrationInfrastructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "METER_IT", matches = "true")
class CacheWriteGuardIntegrationTest {
    private static IntegrationInfrastructure infra;

    @BeforeAll
    static void setUp() throws Exception {
        infra = new IntegrationInfrastructure();
    }

    @AfterAll
    static void close() {
        infra.close();
    }

    @Test
    void expiredOwnerCannotOverwriteTheNewOwnerAndInvalidationFencesInFlightLoads() throws Exception {
        String key = "meter:it:{" + UUID.randomUUID() + "}";
        var guard = new CacheWriteGuard(infra.redis);
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:16389");
        RedissonClient redisson = Redisson.create(config);
        try {
            var lock = redisson.getFencedLock("lock:" + key);
            long first = lock.tryLockAndGetToken(0, 100, TimeUnit.MILLISECONDS);
            String firstGeneration = guard.begin(key, first);
            // 显式短租约仅用于故障注入，模拟旧请求失去所有权后继续运行。
            Thread.sleep(200);
            long second = lock.tryLockAndGetToken(0, 1, TimeUnit.SECONDS);
            String secondGeneration = guard.begin(key, second);
            assertTrue(second > first);
            assertTrue(guard.write(key, second, secondGeneration, "new", Duration.ofMinutes(1)));
            assertFalse(guard.write(key, first, firstGeneration, "old", Duration.ofMinutes(1)));
            assertEquals("new", infra.redis.opsForValue().get(key));
            guard.invalidate(key);
            assertFalse(guard.write(key, second, secondGeneration, "before-update", Duration.ofMinutes(1)));
            assertFalse(infra.redis.hasKey(key));
            // 元数据丢失时拒绝旧加载结果，不能把Redis重启当作允许旧写入的理由。
            infra.redis.delete(key + ":guard");
            assertFalse(guard.write(key, second, secondGeneration, "stale", Duration.ofMinutes(1)));
            lock.unlock();
        } finally {
            redisson.shutdown();
            infra.redis.delete(List.of(key, key + ":guard"));
        }
    }
}
