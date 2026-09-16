package com.byy.meterreading.vo.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员查询用户列表和用户详情时返回的用户信息。
 *
 * @param userId      用户 ID
 * @param username    登录用户名
 * @param displayName 用户显示名称
 * @param status      用户状态：0 禁用，1 启用
 * @param roles       用户拥有的角色编码
 * @param createdAt   创建时间
 * @param updatedAt   最后更新时间
 */
public record AdminUserVO(
        Long userId,
        String username,
        String displayName,
        Integer status,
        List<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public AdminUserVO {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
