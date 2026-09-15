package com.byy.meterreading.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户注册请求参数。
 *
 * @param username    登录用户名
 * @param password    登录明文密码，由服务端使用 BCrypt 加密后保存
 * @param displayName 用户显示名称
 */
public record RegisterDTO(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度必须为8到128个字符")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称长度不能超过64个字符")
        String displayName
) {
}
