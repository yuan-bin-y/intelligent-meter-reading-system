package com.byy.meterreading.auth.service;

import com.byy.meterreading.dto.user.UserPageQueryDTO;
import com.byy.meterreading.dto.user.UpdateUserRolesDTO;
import com.byy.meterreading.dto.user.UpdateUserStatusDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.user.AdminUserVO;

/**
 * 管理员用户管理业务。
 */
public interface AdminUserService {

    /**
     * 按用户名、状态和角色筛选并分页查询用户。
     */
    PageVO<AdminUserVO> listUsers(UserPageQueryDTO queryDTO);

    /**
     * 根据用户 ID 查询用户详情。
     *
     * @param userId 用户 ID
     * @return 用户详情
     */
    AdminUserVO getUser(Long userId);

    /**
     * 管理员启用或禁用指定用户。
     *
     * @param currentAdminId     当前管理员 ID
     * @param targetUserId       目标用户 ID
     * @param updateUserStatusDTO 目标用户状态
     * @return 更新后的用户信息
     */
    AdminUserVO updateUserStatus(
            Long currentAdminId,
            Long targetUserId,
            UpdateUserStatusDTO updateUserStatusDTO
    );

    /**
     * 管理员重新设置指定用户拥有的角色。
     *
     * @param currentAdminId   当前管理员 ID
     * @param targetUserId     目标用户 ID
     * @param updateUserRolesDTO 新的角色编码集合
     * @return 更新后的用户信息
     */
    AdminUserVO updateUserRoles(
            Long currentAdminId,
            Long targetUserId,
            UpdateUserRolesDTO updateUserRolesDTO
    );
}
