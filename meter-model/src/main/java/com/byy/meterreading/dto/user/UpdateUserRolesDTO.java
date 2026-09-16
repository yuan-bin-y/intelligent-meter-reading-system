package com.byy.meterreading.dto.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;

/**
 * 管理员修改用户角色的请求参数。
 *
 * @param roles 新的角色编码集合
 */
public record UpdateUserRolesDTO(
        @NotEmpty(message = "用户角色不能为空")
        @Valid
        List<
                @NotBlank(message = "角色编码不能为空")
                @Size(max = 64, message = "角色编码长度不能超过64个字符")
                String
                > roles
) {

    public UpdateUserRolesDTO {
        if (roles != null) {
            roles = roles.stream()
                    .map(role -> role == null
                            ? null
                            : role.trim().toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();
        }
    }
}
