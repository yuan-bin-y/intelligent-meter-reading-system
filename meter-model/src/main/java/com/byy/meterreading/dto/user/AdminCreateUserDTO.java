package com.byy.meterreading.dto.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;

/**
 * 管理员创建用户的请求参数。
 *
 * @param username    登录用户名
 * @param password    初始明文密码
 * @param displayName 用户显示名称
 * @param status      用户状态：0 禁用，1 启用
 * @param roles       初始角色编码集合
 */
public record AdminCreateUserDTO(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度必须为8到128个字符")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称长度不能超过64个字符")
        String displayName,

        @NotNull(message = "用户状态不能为空")
        @Min(value = 0, message = "用户状态只能是0或1")
        @Max(value = 1, message = "用户状态只能是0或1")
        Integer status,

        @NotEmpty(message = "用户角色不能为空")
        @Valid
        List<
                @NotBlank(message = "角色编码不能为空")
                @Size(max = 64, message = "角色编码长度不能超过64个字符")
                String
                > roles
) {

    public AdminCreateUserDTO {
        username = trimToNull(username);
        displayName = trimToNull(displayName);
        if (roles != null) {
            roles = roles.stream()
                    .map(role -> role == null
                            ? null
                            : role.trim().toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
