package com.byy.meterreading.vo.auth;

import java.util.List;

/**
 * 登录成功响应数据。
 */
public record LoginVO(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String username,
        String displayName,
        List<String> roles
) {

    public LoginVO {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
