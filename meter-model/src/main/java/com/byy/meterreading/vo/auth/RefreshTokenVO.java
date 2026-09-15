package com.byy.meterreading.vo.auth;

/**
 * Refresh Token 轮换成功后的响应数据。
 *
 * @param accessToken      新的 Access Token
 * @param refreshToken     新的 Refresh Token
 * @param tokenType        Token 类型，固定为 Bearer
 * @param expiresIn        Access Token 剩余有效秒数
 * @param refreshExpiresIn Refresh Token 剩余有效秒数
 */
public record RefreshTokenVO(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn
) {
}
