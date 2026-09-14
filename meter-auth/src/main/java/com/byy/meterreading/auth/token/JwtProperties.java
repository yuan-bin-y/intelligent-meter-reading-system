package com.byy.meterreading.auth.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT 签发配置；密钥必须通过环境变量注入，不能写入源码。
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT 签发方不能为空") String issuer,
        @NotNull(message = "JWT 有效期不能为空") Duration accessTokenTtl,
        @NotBlank(message = "JWT 密钥不能为空") String secretBase64
) {
}
