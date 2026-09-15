package com.byy.meterreading.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 刷新 Token 的请求参数。
 * 用户 ID、sid 和 jti 均从验证通过的 Refresh Token 中读取。
 *
 * @param refreshToken 当前有效的 Refresh Token
 */
public record RefreshTokenDTO(
        @NotBlank(message = "Refresh Token 不能为空")
        @Size(max = 4096, message = "Refresh Token 长度不正确")
        String refreshToken
) {
}
