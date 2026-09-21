package com.byy.meterreading.aisimulator.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/** AI 模拟器、回调认证和 RabbitMQ 消费策略配置。 */
@Validated
@ConfigurationProperties(prefix = "app.ai-simulator")
public record AiSimulatorProperties(
        @NotBlank String backendBaseUrl,
        @NotBlank String serviceId,
        @NotBlank String secretBase64,
        @NotBlank String modelName,
        @NotBlank String modelVersion,
        @NotNull AiSimulatorMode mode,
        @NotNull @DecimalMin("0") BigDecimal recognizedValue,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal confidence,
        @NotNull Duration processingDelay,
        @NotNull Duration callbackTimeout,
        @NotNull Duration publisherConfirmTimeout,
        @Positive int maxConsumeAttempts,
        @NotBlank String failureCode,
        @NotBlank String failureMessage
) {

    public AiSimulatorProperties {
        requireNonNegative(processingDelay, "模拟识别耗时");
        requirePositive(callbackTimeout, "回调超时时间");
        requirePositive(publisherConfirmTimeout, "消息确认超时时间");
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + "必须大于0");
        }
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value != null && value.isNegative()) {
            throw new IllegalArgumentException(name + "不能小于0");
        }
    }
}
