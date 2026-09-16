package com.byy.meterreading.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置用户密码的请求参数。
 *
 * @param newPassword 新的明文密码
 */
public record AdminResetPasswordDTO(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度必须为8到128个字符")
        String newPassword
) {
}
