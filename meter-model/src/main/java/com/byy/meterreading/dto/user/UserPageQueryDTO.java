package com.byy.meterreading.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * 管理员分页查询用户列表的请求参数。
 *
 * @param page     页码，从 1 开始，默认 1
 * @param pageSize 每页数量，默认 20，最大 100
 * @param username 用户名关键字，支持模糊查询
 * @param status   用户状态：0 禁用，1 启用
 * @param roleCode 角色编码
 */
public record UserPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize,

        @Size(max = 64, message = "用户名关键字长度不能超过64个字符")
        String username,

        @Min(value = 0, message = "用户状态只能是0或1")
        @Max(value = 1, message = "用户状态只能是0或1")
        Integer status,

        @Size(max = 64, message = "角色编码长度不能超过64个字符")
        String roleCode
) {

    public UserPageQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        username = trimToNull(username);
        roleCode = trimToNull(roleCode);
        if (roleCode != null) {
            roleCode = roleCode.toUpperCase(Locale.ROOT);
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
