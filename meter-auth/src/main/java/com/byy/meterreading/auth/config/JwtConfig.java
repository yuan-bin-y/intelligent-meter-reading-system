package com.byy.meterreading.auth.config;

import com.byy.meterreading.auth.token.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
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
     * 解码器负责验证 HS256 签名、Token 有效期和签发方。
     */
    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties properties
    ) {
        // 1. 使用同一把密钥和 HS256 算法验证 JWT 签名
        NimbusJwtDecoder jwtDecoder =
                NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        // 2. 在默认时间校验基础上增加 issuer 校验
        jwtDecoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(properties.issuer())
        );

        return jwtDecoder;
    }
}
