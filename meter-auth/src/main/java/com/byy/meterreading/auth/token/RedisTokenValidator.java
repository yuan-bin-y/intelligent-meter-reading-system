package com.byy.meterreading.auth.token;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 在 JWT 签名、签发方和过期时间校验之外，继续检查 Redis 登录会话。
 */
@Component
public class RedisTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token",
            "登录状态已失效，请重新登录",
            null
    );

    private final RedisAuthSessionService redisAuthSessionService;

    public RedisTokenValidator(
            RedisAuthSessionService redisAuthSessionService
    ) {
        this.redisAuthSessionService = redisAuthSessionService;
    }

    /**
     * JWT 中必须包含有效的 userId 和 jti，并且对应的 Redis 会话仍然存在。
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        String sessionId = jwt.getClaimAsString(
                JwtTokenService.CLAIM_SESSION_ID
        );
        String tokenType = jwt.getClaimAsString(
                JwtTokenService.CLAIM_TOKEN_TYPE
        );
        String tokenId = jwt.getId();

        if (!(userIdClaim instanceof Number userId)
                || sessionId == null
                || sessionId.isBlank()
                || tokenId == null
                || tokenId.isBlank()
                || !JwtTokenService.TOKEN_TYPE_ACCESS.equals(tokenType)
                || !redisAuthSessionService.isSessionActive(
                        userId.longValue(),
                        sessionId
                )) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        return OAuth2TokenValidatorResult.success();
    }
}
