package com.byy.meterreading.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 使用 Redis 管理用户的登录会话和 Refresh Token 轮换状态。
 *
 * <p>一个 session Hash 保存用户 ID 和当前有效的 Refresh Token jti，
 * 一个 sessions Set 保存该用户的全部 sid，从而支持当前设备退出和全部设备退出。</p>
 */
@Service
public class RedisAuthSessionService {

    private static final String KEY_PREFIX = "meter:auth:";
    private static final String FIELD_USER_ID = "userId";

    /**
     * 原子创建会话 Hash、加入用户会话集合，并为两个 Key 设置相同 TTL。
     */
    private static final DefaultRedisScript<Long> ACTIVATE_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('HSET', KEYS[1],
                            'userId', ARGV[1],
                            'refreshJti', ARGV[2])
                    redis.call('PEXPIRE', KEYS[1], ARGV[3])
                    redis.call('SADD', KEYS[2], ARGV[4])
                    redis.call('PEXPIRE', KEYS[2], ARGV[3])
                    return 1
                    """, Long.class);

    /**
     * 只有旧 refreshJti 与 Redis 当前值一致时，才原子替换成新值。
     */
    private static final DefaultRedisScript<Long> ROTATE_REFRESH_SCRIPT =
            new DefaultRedisScript<>("""
                    local storedUserId = redis.call('HGET', KEYS[1], 'userId')
                    local storedRefreshJti = redis.call('HGET', KEYS[1], 'refreshJti')
                    if storedUserId ~= ARGV[1]
                            or storedRefreshJti ~= ARGV[2] then
                        return 0
                    end
                    redis.call('HSET', KEYS[1], 'refreshJti', ARGV[3])
                    redis.call('PEXPIRE', KEYS[1], ARGV[4])
                    redis.call('PEXPIRE', KEYS[2], ARGV[4])
                    return 1
                    """, Long.class);

    /**
     * 原子删除当前会话并从用户会话集合中移除 sid。
     */
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('DEL', KEYS[1])
                    redis.call('SREM', KEYS[2], ARGV[1])
                    if redis.call('SCARD', KEYS[2]) == 0 then
                        redis.call('DEL', KEYS[2])
                    end
                    return 1
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisAuthSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 登录成功后创建会话，并登记当前有效的 Refresh Token jti。
     */
    public void activate(
            Long userId,
            String sid,
            String refreshJti,
            Duration ttl
    ) {
        requireSessionArguments(userId, sid);
        if (refreshJti == null || refreshJti.isBlank()) {
            throw new IllegalArgumentException("refreshJti 不能为空");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("会话有效期必须大于 0");
        }

        redisTemplate.execute(
                ACTIVATE_SCRIPT,
                List.of(sessionKey(userId, sid), userSessionsKey(userId)),
                userId.toString(),
                refreshJti,
                Long.toString(ttl.toMillis()),
                sid
        );
    }

    /**
     * 检查 sid 对应的 Redis 会话是否存在，并确认它属于指定用户。
     */
    public boolean isSessionActive(Long userId, String sid) {
        if (userId == null || sid == null || sid.isBlank()) {
            return false;
        }

        Object storedUserId = redisTemplate.opsForHash()
                .get(sessionKey(userId, sid), FIELD_USER_ID);
        return userId.toString().equals(storedUserId);
    }

    /**
     * 原子校验旧 Refresh Token jti 并替换为新的 jti。
     * 返回 false 表示会话不存在，或旧 Refresh Token 已经失效、被重复使用。
     */
    public boolean rotateRefreshToken(
            Long userId,
            String sid,
            String oldRefreshJti,
            String newRefreshJti,
            Duration ttl
    ) {
        requireSessionArguments(userId, sid);
        if (oldRefreshJti == null || oldRefreshJti.isBlank()
                || newRefreshJti == null || newRefreshJti.isBlank()) {
            return false;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("会话有效期必须大于 0");
        }

        Long result = redisTemplate.execute(
                ROTATE_REFRESH_SCRIPT,
                List.of(sessionKey(userId, sid), userSessionsKey(userId)),
                userId.toString(),
                oldRefreshJti,
                newRefreshJti,
                Long.toString(ttl.toMillis())
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 撤销当前 sid 对应的整个登录会话。
     */
    public void revoke(Long userId, String sid) {
        if (userId == null || sid == null || sid.isBlank()) {
            return;
        }

        redisTemplate.execute(
                REVOKE_SCRIPT,
                List.of(sessionKey(userId, sid), userSessionsKey(userId)),
                sid
        );
    }

    /**
     * 撤销指定用户的全部登录会话，供修改密码和强制下线使用。
     */
    public void revokeAll(Long userId) {
        if (userId == null) {
            return;
        }

        String userSessionsKey = userSessionsKey(userId);
        Set<String> sessionIds = redisTemplate.opsForSet()
                .members(userSessionsKey);

        if (sessionIds != null && !sessionIds.isEmpty()) {
            List<String> sessionKeys = sessionIds.stream()
                    .map(sid -> sessionKey(userId, sid))
                    .toList();
            redisTemplate.delete(sessionKeys);
        }

        redisTemplate.delete(userSessionsKey);
    }

    private void requireSessionArguments(Long userId, String sid) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (sid == null || sid.isBlank()) {
            throw new IllegalArgumentException("sid 不能为空");
        }
    }

    /**
     * 同一用户的 Key 使用相同哈希标签，支持 Redis Cluster 的多 Key Lua 操作。
     */
    private String sessionKey(Long userId, String sid) {
        return KEY_PREFIX + "{" + userId + "}:session:" + sid;
    }

    private String userSessionsKey(Long userId) {
        return KEY_PREFIX + "{" + userId + "}:sessions";
    }
}
