package com.byy.meterreading.vo.auth;

import java.util.List;

/**
 * 返回给客户端的当前登录用户信息。
 *
 * @param userId      用户 ID
 * @param username    登录用户名
 * @param displayName 显示名称
 * @param roles       当前启用的角色编码
 */
public record CurrentUserVO(
        Long userId,
        String username,
        String displayName,
        List<String> roles
) {

    /**
     * 对角色集合进行不可变复制，避免响应数据被外部修改。
     */
    public CurrentUserVO {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
