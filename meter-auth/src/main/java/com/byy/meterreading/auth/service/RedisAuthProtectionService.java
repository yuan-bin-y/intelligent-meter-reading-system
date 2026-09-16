package com.byy.meterreading.auth.service;

import com.byy.meterreading.auth.config.AuthProtectionProperties;
import com.byy.meterreading.auth.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 使用 Redis 保护登录和注册接口。
 *
 * <p>负责接口固定窗口限流、登录失败计数和账号临时锁定。
 * 用户名和 IP 在写入 Redis Key 前会先生成摘要，避免直接暴露原始信息。</p>
 */
@Service
public class RedisAuthProtectionService {

    private static final Logger log = LoggerFactory.getLogger(
            RedisAuthProtectionService.class
    );

    private static final String KEY_PREFIX = "meter:auth:protection:";

    private static final Duration LOGIN_RATE_WINDOW = Duration.ofMinutes(1);

    private static final Duration REGISTER_RATE_WINDOW = Duration.ofHours(1);

    /**
     * 原子增加固定窗口内的请求次数，并在第一次计数时设置过期时间。
     */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return current
                    """, Long.class);

    /**
     * 原子记录登录失败次数；达到阈值后创建锁定 Key，并清除失败计数。
     */
    private static final DefaultRedisScript<Long> LOGIN_FAILURE_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    if current >= tonumber(ARGV[2]) then
                        redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
                        redis.call('DEL', KEYS[1])
                    end
                    return current
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthProtectionProperties properties;

    public RedisAuthProtectionService(
            StringRedisTemplate redisTemplate,
            AuthProtectionProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 按配置限制同一个 IP 和用户名每分钟请求登录接口的次数。
     */
    public void checkLoginRate(String clientIp, String username) {
        String subject = normalizeClientIp(clientIp)
                + ":"
                + normalizeUsername(username);
        checkFixedWindow(
                "login",
                subject,
                properties.loginPerMinute(),
                LOGIN_RATE_WINDOW,
                "登录请求过于频繁，请稍后再试"
        );
    }

    /**
     * 按配置限制同一个 IP 每小时请求注册接口的次数。
     */
    public void checkRegisterRate(String clientIp) {
        checkFixedWindow(
                "register",
                normalizeClientIp(clientIp),
                properties.registerPerHour(),
                REGISTER_RATE_WINDOW,
                "注册请求过于频繁，请稍后再试"
        );
    }

    /**
     * 认证前检查用户名是否因为连续失败而处于临时锁定状态。
     */
    public void checkLoginAllowed(String username) {
        String subjectDigest = digest(normalizeUsername(username));
        try {
            if (Boolean.TRUE.equals(
                    redisTemplate.hasKey(loginLockKey(subjectDigest)))) {
                throw new RateLimitExceededException(
                        loginLockedMessage()
                );
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            // 认证保护属于防护层，Redis 短暂不可用时记录日志并继续正常认证。
            log.warn("Redis 登录锁定状态暂时不可用", exception);
        }
    }

    /**
     * 密码认证失败后增加失败次数，达到配置阈值后临时锁定该用户名。
     */
    public void recordLoginFailure(String username) {
        String subjectDigest = digest(normalizeUsername(username));
        try {
            Long failures = redisTemplate.execute(
                    LOGIN_FAILURE_SCRIPT,
                    List.of(
                            loginFailureKey(subjectDigest),
                            loginLockKey(subjectDigest)
                    ),
                    Long.toString(properties.loginFailureWindow().toMillis()),
                    Integer.toString(properties.maxLoginFailures()),
                    Long.toString(properties.loginLockDuration().toMillis())
            );

            if (failures != null
                    && failures >= properties.maxLoginFailures()) {
                throw new RateLimitExceededException(
                        loginLockedMessage()
                );
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("Redis 登录失败次数记录暂时不可用", exception);
        }
    }

    /**
     * 登录成功后清除尚未达到锁定阈值的失败次数。
     */
    public void clearLoginFailures(String username) {
        String subjectDigest = digest(normalizeUsername(username));
        try {
            redisTemplate.delete(loginFailureKey(subjectDigest));
        } catch (DataAccessException exception) {
            log.warn("Redis 登录失败次数清理暂时不可用", exception);
        }
    }

    /**
     * 管理员主动解除登录锁定，同时清除失败次数。
     *
     * <p>该操作不降级处理 Redis 异常，删除失败时由接口返回 500，
     * 避免管理员误以为锁定状态已经解除。</p>
     */
    public void clearLoginLock(String username) {
        String subjectDigest = digest(normalizeUsername(username));
        redisTemplate.delete(List.of(
                loginFailureKey(subjectDigest),
                loginLockKey(subjectDigest)
        ));
    }

    /**
     * 执行固定窗口计数，超过窗口允许次数时拒绝当前请求。
     */
    private void checkFixedWindow(
            String scope,
            String subject,
            int limit,
            Duration window,
            String message
    ) {
        long windowNumber = Instant.now().toEpochMilli() / window.toMillis();
        String key = KEY_PREFIX
                + "rate:"
                + scope
                + ":"
                + digest(subject)
                + ":"
                + windowNumber;

        try {
            Long current = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(key),
                    Long.toString(window.plusSeconds(5).toMillis())
            );
            if (current != null && current > limit) {
                throw new RateLimitExceededException(message);
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("Redis 认证接口限流暂时不可用，scope={}", scope, exception);
        }
    }

    private String loginFailureKey(String subjectDigest) {
        return KEY_PREFIX + "{" + subjectDigest + "}:login:fail";
    }

    private String loginLockKey(String subjectDigest) {
        return KEY_PREFIX + "{" + subjectDigest + "}:login:lock";
    }

    private String loginLockedMessage() {
        long minutes = properties.loginLockDuration().toMinutes();
        if (minutes > 0) {
            return "登录失败次数过多，请" + minutes + "分钟后重试";
        }
        return "登录失败次数过多，请稍后重试";
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeClientIp(String clientIp) {
        return clientIp == null || clientIp.isBlank()
                ? "unknown"
                : clientIp.trim();
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前运行环境不支持 SHA-256",
                    exception
            );
        }
    }
}
