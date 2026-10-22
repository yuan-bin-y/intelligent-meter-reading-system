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
    private Duration ttl = Duration.ofSeconds(60);
    private Duration ttlJitter = Duration.ofSeconds(15);
    private Duration nullTtl = Duration.ofSeconds(45);
    private Duration lockWait = Duration.ofSeconds(1);
    private Duration loadTimeout = Duration.ofSeconds(5);
    private int loadConcurrency = 8;

    @PostConstruct
    void validate() {
        requirePositive(ttl, "BUSINESS_CACHE_TTL");
        requireNotNegative(ttlJitter, "BUSINESS_CACHE_TTL_JITTER");
        requirePositive(nullTtl, "BUSINESS_CACHE_NULL_TTL");
        requireNotNegative(lockWait, "BUSINESS_CACHE_LOCK_WAIT");
        requirePositive(loadTimeout, "BUSINESS_CACHE_LOAD_TIMEOUT");
        if (loadConcurrency < 1 || loadConcurrency > 64) {
            throw new IllegalStateException("BUSINESS_CACHE_LOAD_CONCURRENCY必须在1至64之间");
        }
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

    public Duration getLoadTimeout() {
        return loadTimeout;
    }

    public void setLoadTimeout(Duration loadTimeout) {
        this.loadTimeout = loadTimeout;
    }

    public int getLoadConcurrency() {
        return loadConcurrency;
    }

    public void setLoadConcurrency(int loadConcurrency) {
        this.loadConcurrency = loadConcurrency;
    }

}
