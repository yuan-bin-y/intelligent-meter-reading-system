package com.byy.meterreading.auth.service;

import com.byy.meterreading.dto.user.AdminCreateUserDTO;
import com.byy.meterreading.dto.user.AdminResetPasswordDTO;
import com.byy.meterreading.dto.user.UserPageQueryDTO;
import com.byy.meterreading.dto.user.UpdateUserRolesDTO;
import com.byy.meterreading.dto.user.UpdateUserProfileDTO;
import com.byy.meterreading.dto.user.UpdateUserStatusDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.user.AdminUserVO;
import com.byy.meterreading.vo.user.AssignableRoleVO;

import java.util.List;

/**
 * 管理员用户管理业务。
 */
public interface AdminUserService {

    /**
     * 创建用户并分配初始角色。
     */
    AdminUserVO createUser(AdminCreateUserDTO adminCreateUserDTO);

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

    /**
     * 修改用户显示名称。
     */
    AdminUserVO updateUserProfile(
            Long targetUserId,
            UpdateUserProfileDTO updateUserProfileDTO
    );

    /**
     * 管理员重置用户密码。
     */
    void resetUserPassword(
            Long targetUserId,
            AdminResetPasswordDTO adminResetPasswordDTO
    );

    /**
     * 查询全部可以分配的启用角色。
     */
    List<AssignableRoleVO> listAssignableRoles();

    /**
     * 清除用户的登录失败次数和临时锁定状态。
     */
    void clearLoginLock(Long targetUserId);
}
