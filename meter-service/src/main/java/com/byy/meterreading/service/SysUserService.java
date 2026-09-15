package com.byy.meterreading.service;

import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.model.SysRole;

import java.time.LocalDateTime;
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
     * 判断用户名是否已经存在。
     */
    boolean existsByUsername(String username);

    /**
     * 新增用户，插入成功后数据库生成的主键会回填到实体中。
     */
    void createUser(SysUser user);

    /**
     * 根据角色编码查询启用状态的角色。
     */
    SysRole findEnabledRoleByCode(String roleCode);

    /**
     * 为用户绑定角色。
     */
    void bindRole(Long userId, Long roleId);

    /**
     * 更新启用用户的密码哈希和修改时间。
     *
     * @return 受影响行数，1 表示成功，0 表示用户不存在或已被禁用
     */
    int updatePasswordHash(
            Long userId,
            String passwordHash,
            LocalDateTime updatedAt
    );

    /**
     * 查询用户拥有的角色编码。
     *
     * @param userId 用户 ID
     * @return 角色编码集合
     */
    List<String> findRoleCodesByUserId(Long userId);
}
