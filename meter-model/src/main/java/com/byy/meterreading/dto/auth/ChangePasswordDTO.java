package com.byy.meterreading.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 当前登录用户修改密码的请求参数。
 * 用户身份由 JWT 确定，因此不接收用户名或用户 ID。
 *
 * @param oldPassword 当前使用的明文密码
 * @param newPassword 准备设置的新明文密码
 */
public record ChangePasswordDTO(
        @NotBlank(message = "原密码不能为空")
        @Size(max = 128, message = "原密码长度不能超过128个字符")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 128, message = "新密码长度必须为8到128个字符")
        String newPassword
) {
}
