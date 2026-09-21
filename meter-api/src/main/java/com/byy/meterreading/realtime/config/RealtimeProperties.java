package com.byy.meterreading.realtime.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** SSE 连接、断线补偿和 Redis Pub/Sub 配置。 */
@Validated
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
        @NotNull Duration timeout,
        @Min(1) long heartbeatIntervalMs,
        @Min(1) @Max(500) int replayLimit,
        @NotBlank String redisChannel
) {
}
