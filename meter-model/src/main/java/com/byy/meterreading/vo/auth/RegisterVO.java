package com.byy.meterreading.vo.auth;

import java.util.List;

/**
 * 用户注册成功后的响应数据。
 *
 * @param userId      新用户 ID
 * @param username    登录用户名
 * @param displayName 用户显示名称
 * @param roles       注册时分配的角色编码
 */
public record RegisterVO(
        Long userId,
        String username,
        String displayName,
        List<String> roles
) {

    /**
     * 对角色集合进行不可变复制，避免响应数据被外部修改。
     */
    public RegisterVO {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
