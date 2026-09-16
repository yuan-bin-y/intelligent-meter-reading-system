package com.byy.meterreading.vo.user;

/**
 * 管理员创建用户和分配角色时可选择的角色。
 *
 * @param roleId   角色 ID
 * @param roleCode 角色编码
 * @param roleName 角色名称
 */
public record AssignableRoleVO(
        Long roleId,
        String roleCode,
        String roleName
) {
}
