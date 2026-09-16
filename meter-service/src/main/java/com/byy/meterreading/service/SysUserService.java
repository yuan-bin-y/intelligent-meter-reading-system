package com.byy.meterreading.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.dto.user.UserPageQueryDTO;
import com.byy.meterreading.mapper.projection.UserRoleCodeRow;
import com.byy.meterreading.model.SysRole;
import com.byy.meterreading.model.SysUser;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * 按管理员用户列表的查询条件分页查询用户。
     *
     * @param page     MyBatis-Plus 分页参数
     * @param queryDTO 用户筛选条件
     * @return 用户分页结果
     */
    IPage<SysUser> pageAdminUsers(
            Page<SysUser> page,
            UserPageQueryDTO queryDTO
    );

    /**
     * 批量查询多个用户拥有的有效角色编码。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID 与角色编码的对应关系；集合为空时返回空列表
     */
    List<UserRoleCodeRow> findRoleCodesByUserIds(Collection<Long> userIds);
}
