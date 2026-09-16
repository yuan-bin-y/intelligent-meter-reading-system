package com.byy.meterreading.mapper.projection;

/**
 * 用户角色批量查询结果，只承载用户 ID 与角色编码。
 *
 * @param userId   用户 ID
 * @param roleCode 角色编码
 */
public record UserRoleCodeRow(
        Long userId,
        String roleCode
) {
}
