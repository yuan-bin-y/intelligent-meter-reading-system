package com.byy.meterreading.auth.token;

import com.byy.meterreading.auth.security.CustomUserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtTokenService {

    public static final String CLAIM_SESSION_ID = "sid";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    // jwtEncoder = 负责生成、签名 JWT；properties = JWT 签发配置
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder jwtEncoder,
                           JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * 登录认证成功后，为同一个登录会话签发 Access Token 和 Refresh Token。
     */
    public IssuedTokenPair issue(CustomUserDetails userDetails) {
        return issueForSession(
                userDetails,
                UUID.randomUUID().toString()
        );
    }

    /**
     * 刷新 Token 时沿用原登录会话 sid，并轮换两种 Token 的 jti。
     */
    public IssuedTokenPair rotate(
            CustomUserDetails userDetails,
            String sessionId
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        return issueForSession(userDetails, sessionId);
    }

    /**
     * 为指定登录会话签发一组新的 Access Token 和 Refresh Token。
     */
    private IssuedTokenPair issueForSession(
            CustomUserDetails userDetails,
            String sessionId
    ) {
        // 1. 获取当前时间以及两种 Token 的有效期
        Instant issuedAt = Instant.now();
        Duration accessTokenTtl = properties.accessTokenTtl();
        Duration refreshTokenTtl = properties.refreshTokenTtl();

        // 2. 每一枚 Token 使用独立的 jti，sid 由首次登录创建并在刷新时保持不变
        String accessTokenId = UUID.randomUUID().toString();
        String refreshTokenId = UUID.randomUUID().toString();

        // 3. 分别计算 Access Token 和 Refresh Token 的过期时间
        Instant accessExpiresAt = issuedAt.plus(accessTokenTtl);
        Instant refreshExpiresAt = issuedAt.plus(refreshTokenTtl);

        // 4. Access Token 携带访问业务接口所需的用户和角色信息
        JwtClaimsSet accessClaims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(accessExpiresAt)
                .subject(userDetails.getUsername())
                .id(accessTokenId)
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim("userId", userDetails.getUserId())
                .claim("roles", userDetails.getRoles())
                .build();

        // 5. Refresh Token 只携带刷新所需信息，不保存角色权限
        JwtClaimsSet refreshClaims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(refreshExpiresAt)
                .subject(userDetails.getUsername())
                .id(refreshTokenId)
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .claim("userId", userDetails.getUserId())
                .build();

        // 6. 使用同一把密钥分别签名两种 JWT
        String accessToken = encode(accessClaims);
        String refreshToken = encode(refreshClaims);

        // 7. 返回认证模块内部使用的 Token 对及其会话信息
        return new IssuedTokenPair(
                accessToken,
                refreshToken,
                sessionId,
                refreshTokenId,
                accessTokenTtl.toSeconds(),
                refreshTokenTtl.toSeconds()
        );
    }

    /**
     * 调用 Spring Security 提供的 JwtEncoder 完成签名并取出 Token 字符串。
     */
    private String encode(JwtClaimsSet claims) {
        Jwt jwt = jwtEncoder.encode(
                JwtEncoderParameters.from(claims)
        );
        return jwt.getTokenValue();
    }
}
