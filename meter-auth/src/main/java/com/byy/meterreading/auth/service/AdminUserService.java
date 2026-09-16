package com.byy.meterreading.auth.service;

import com.byy.meterreading.dto.user.UserPageQueryDTO;
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
}
