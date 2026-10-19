package com.byy.meterreading.vo.auth;

import java.util.List;

/**
 * 登录成功响应数据。
 *
 * @param accessToken      用于访问业务接口的 Access Token
 * @param refreshToken     用于换取新 Token 的 Refresh Token
 * @param tokenType        Token 类型，固定为 Bearer
 * @param expiresIn        Access Token 剩余有效秒数
 * @param refreshExpiresIn Refresh Token 剩余有效秒数
 * @param userId           用户 ID
 * @param username         登录用户名
 * @param displayName      用户显示名称
 * @param roles            用户角色编码
 */
public record LoginVO(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        Long userId,
        String username,
        String displayName,
        List<String> roles
) {

    public LoginVO {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
