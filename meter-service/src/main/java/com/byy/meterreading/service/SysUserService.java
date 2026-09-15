package com.byy.meterreading.service;

import com.byy.meterreading.model.SysUser;

import java.util.List;

public interface SysUserService {

    /**
     * 根据用户 ID 查询用户。
     *
     * @param userId 用户 ID
     * @return 用户不存在时返回 null
     */
    SysUser findById(Long userId);

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名
     * @return 用户不存在时返回 null
     */
    SysUser findByUsername(String username);

    /**
     * 查询用户拥有的角色编码。
     *
     * @param userId 用户 ID
     * @return 角色编码集合
     */
    List<String> findRoleCodesByUserId(Long userId);
}
