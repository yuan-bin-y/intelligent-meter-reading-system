package com.byy.meterreading.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员启用或禁用用户的请求参数。
 *
 * @param status 用户状态：0 禁用，1 启用
 */
public record UpdateUserStatusDTO(
        @NotNull(message = "用户状态不能为空")
        @Min(value = 0, message = "用户状态只能是0或1")
        @Max(value = 1, message = "用户状态只能是0或1")
        Integer status
) {
}
