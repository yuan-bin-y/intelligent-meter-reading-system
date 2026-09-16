package com.byy.meterreading.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员修改用户基本资料的请求参数。
 *
 * @param displayName 用户显示名称
 */
public record UpdateUserProfileDTO(
        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称长度不能超过64个字符")
        String displayName
) {

    public UpdateUserProfileDTO {
        displayName = displayName == null ? null : displayName.trim();
    }
}
