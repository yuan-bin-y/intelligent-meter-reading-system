package com.byy.meterreading.cache;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 表具、设备和角色等只读业务数据的 Redis 缓存配置。
 */
@Component
@ConfigurationProperties(prefix = "app.cache.business")
public class BusinessCacheProperties {

    private boolean enabled = true;
    private Duration ttl = Duration.ofMinutes(10);
    private Duration ttlJitter = Duration.ofMinutes(2);
    private Duration nullTtl = Duration.ofSeconds(45);
    private Duration lockWait = Duration.ofSeconds(1);
    private Duration lockLease = Duration.ofSeconds(10);

    @PostConstruct
    void validate() {
        requirePositive(ttl, "BUSINESS_CACHE_TTL");
        requireNotNegative(ttlJitter, "BUSINESS_CACHE_TTL_JITTER");
        requirePositive(nullTtl, "BUSINESS_CACHE_NULL_TTL");
        requireNotNegative(lockWait, "BUSINESS_CACHE_LOCK_WAIT");
        requirePositive(lockLease, "BUSINESS_CACHE_LOCK_LEASE");
    }

    private void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " 必须大于0");
        }
    }

    private void requireNotNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalStateException(name + " 不能小于0");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getTtlJitter() {
        return ttlJitter;
    }

    public void setTtlJitter(Duration ttlJitter) {
        this.ttlJitter = ttlJitter;
    }

    public Duration getNullTtl() {
        return nullTtl;
    }

    public void setNullTtl(Duration nullTtl) {
        this.nullTtl = nullTtl;
    }

    public Duration getLockWait() {
        return lockWait;
    }

    public void setLockWait(Duration lockWait) {
        this.lockWait = lockWait;
    }

    public Duration getLockLease() {
        return lockLease;
    }

    public void setLockLease(Duration lockLease) {
        this.lockLease = lockLease;
    }
}
