package com.byy.meterreading.auth.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * AI 视觉服务回调认证配置；共享密钥必须通过环境变量注入。
 */
@Validated
@ConfigurationProperties(prefix = "app.security.ai-service")
public record AiServiceProperties(
        @NotBlank(message = "AI服务编号不能为空")
        String serviceId,

        @NotBlank(message = "AI服务认证密钥不能为空")
        String secretBase64,

        @NotNull(message = "AI服务请求时间误差不能为空")
        Duration allowedClockSkew,

        @NotNull(message = "AI服务随机数有效期不能为空")
        Duration nonceTtl,

        @Positive(message = "AI服务请求体大小上限必须大于0")
        int maxBodySize
) {

    public AiServiceProperties {
        if (allowedClockSkew != null
                && (allowedClockSkew.isZero()
                || allowedClockSkew.isNegative())) {
            throw new IllegalArgumentException(
                    "AI服务请求时间误差必须大于0"
            );
        }
        if (nonceTtl != null
                && (nonceTtl.isZero() || nonceTtl.isNegative())) {
            throw new IllegalArgumentException(
                    "AI服务随机数有效期必须大于0"
            );
        }
        if (allowedClockSkew != null
                && nonceTtl != null
                && nonceTtl.compareTo(
                allowedClockSkew.multipliedBy(2)
        ) < 0) {
            throw new IllegalArgumentException(
                    "AI服务随机数有效期不能小于时间误差的两倍"
            );
        }
    }
}
