package com.byy.meterreading.auth.token;

import com.byy.meterreading.auth.security.CustomUserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JwtTokenService {

    // jwtEncoder = 负责生成、签名 JWT；properties = JWT 签发配置
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder jwtEncoder,
                           JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public String generate(CustomUserDetails userDetails) {
        // 1. 接收认证成功的 CustomUserDetails

        // 2. 获取当前时间 issuedAt
        Instant issuedAt = Instant.now();

        // 3. 根据 expiresIn 计算 expiresAt
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        // 4. 创建 JWT Claims
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(userDetails.getUsername())
                .claim("userId", userDetails.getUserId())
                .claim("roles", userDetails.getRoles())
                .build();

        // 5. 调用 JwtEncoder 签名
        Jwt jwt = jwtEncoder.encode(
                JwtEncoderParameters.from(claims)
        );

        // 6. 取出 Token 字符串并返回
        return jwt.getTokenValue();
    }

    public long getExpiresIn() {
        return properties.accessTokenTtl().toSeconds();
    }
}
