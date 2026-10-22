package com.byy.meterreading.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Redis 在同一个 Lua 内校验重建令牌、失效代次并写值，避免先检查再写的竞争窗口。 */
@Component
public class CacheWriteGuard {
    private static final DefaultRedisScript<String> BEGIN = new DefaultRedisScript<>("""
            local old = redis.call('HGET', KEYS[1], 'fence')
            if old and tonumber(old) >= tonumber(ARGV[1]) then return nil end
            local generation = redis.call('HGET', KEYS[1], 'generation') or ARGV[2]
            redis.call('HSET', KEYS[1], 'fence', ARGV[1], 'generation', generation)
            redis.call('EXPIRE', KEYS[1], 86400)
            return generation
            """, String.class);
    private static final DefaultRedisScript<Long> WRITE = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[2], 'fence') ~= ARGV[1] or
               redis.call('HGET', KEYS[2], 'generation') ~= ARGV[2] then return 0 end
            redis.call('SET', KEYS[1], ARGV[3], 'PX', ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[2], 'generation', ARGV[1])
            redis.call('EXPIRE', KEYS[2], 86400)
            return redis.call('DEL', KEYS[1])
            """, Long.class);
    private final StringRedisTemplate redis;

    public CacheWriteGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String begin(String key, long fence) {
        return redis.execute(BEGIN, List.of(key + ":guard"), Long.toString(fence), UUID.randomUUID().toString());
    }

    public boolean write(String key, long fence, String generation, String json, Duration ttl) {
        if (generation == null) {
            return false;
        }
        return Long.valueOf(1).equals(redis.execute(WRITE, List.of(key, key + ":guard"),
                Long.toString(fence), generation, json, Long.toString(ttl.toMillis())));
    }

    public void invalidate(String key) {
        redis.execute(INVALIDATE, List.of(key, key + ":guard"), UUID.randomUUID().toString());
    }
}
