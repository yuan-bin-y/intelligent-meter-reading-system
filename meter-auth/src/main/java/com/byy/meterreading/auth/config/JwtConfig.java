package com.byy.meterreading.auth.config;

import com.byy.meterreading.auth.token.JwtProperties;
import com.byy.meterreading.auth.token.JwtTokenService;
import com.byy.meterreading.auth.token.RedisTokenValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * JWT 签发配置，负责根据外部配置创建 JWT 编码器。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /**
     * 将环境变量提供的 Base64 密钥转换成 HS256 使用的对称密钥。
     */
    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        // 1. 将 Base64 字符串解码为原始密钥字节
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder()
                    .decode(properties.secretBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "JWT_SECRET_BASE64 必须是有效的 Base64 字符串",
                    exception
            );
        }

        // 2. HS256 要求密钥至少为 256 位，即 32 字节
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT_SECRET_BASE64 解码后不能少于 32 字节"
            );
        }

        // 3. 根据密钥字节创建 HMAC-SHA256 对称密钥 Bean
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    /**
     * 使用对称密钥创建 Spring Security 自带的 JWT 编码器。
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * 使用签发时的同一把对称密钥创建 JWT 解码器。
     * 解码器负责验证 HS256 签名、Token 有效期、签发方和 Redis 会话。
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties properties,
            RedisTokenValidator redisTokenValidator
    ) {
        // 1. 使用同一把密钥和 HS256 算法验证 JWT 签名
        NimbusJwtDecoder jwtDecoder =
                NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        // 2. 组合默认时间、issuer 和 Redis 登录会话校验
        jwtDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(
                                properties.issuer()
                        ),
                        redisTokenValidator
                )
        );

        return jwtDecoder;
    }

    /**
     * 创建 Refresh Token 专用解码器，只校验签名、时间、签发方和 Token 类型。
     * Redis 中 refreshJti 的比较和轮换由刷新业务原子完成。
     */
    @Bean("refreshTokenDecoder")
    public JwtDecoder refreshTokenDecoder(
            SecretKey jwtSecretKey,
            JwtProperties properties
    ) {
        NimbusJwtDecoder refreshTokenDecoder =
                NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        OAuth2TokenValidator<Jwt> refreshTokenTypeValidator = jwt -> {
            String tokenType = jwt.getClaimAsString(
                    JwtTokenService.CLAIM_TOKEN_TYPE
            );
            if (JwtTokenService.TOKEN_TYPE_REFRESH.equals(tokenType)) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "Token 类型不是 refresh",
                            null
                    )
            );
        };

        refreshTokenDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(
                                properties.issuer()
                        ),
                        refreshTokenTypeValidator
                )
        );

        return refreshTokenDecoder;
    }
}
