package com.byy.meterreading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 设备心跳配置。
 */
@Component
@ConfigurationProperties(prefix = "app.device.heartbeat")
public class DeviceHeartbeatProperties {

    /**
     * Redis 心跳记录的存活时间；超过该时间仍未收到新心跳则认为设备离线。
     */
    private Duration ttl = Duration.ofSeconds(90);

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("设备心跳 TTL 必须大于 0");
        }
        this.ttl = ttl;
    }
}
