package com.byy.meterreading.auth.token;

/**
 * Access Token 和 Refresh Token 签发后的内部结果。
 * 该对象只在认证模块内部传递，不直接作为 Controller 响应。
 *
 * @param accessToken      Access Token 字符串
 * @param refreshToken     Refresh Token 字符串
 * @param sessionId        本次登录会话的 sid
 * @param refreshTokenId   Refresh Token 的 jti
 * @param accessExpiresIn  Access Token 剩余有效秒数
 * @param refreshExpiresIn Refresh Token 剩余有效秒数
 */
public record IssuedTokenPair(
        String accessToken,
        String refreshToken,
        String sessionId,
        String refreshTokenId,
        long accessExpiresIn,
        long refreshExpiresIn
) {
}
