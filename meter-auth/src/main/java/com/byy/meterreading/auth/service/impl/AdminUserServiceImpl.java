package com.byy.meterreading.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.auth.service.AdminUserService;
import com.byy.meterreading.dto.user.UserPageQueryDTO;
import com.byy.meterreading.mapper.projection.UserRoleCodeRow;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.service.SysUserService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.user.AdminUserVO;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员用户管理业务实现。
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final SysUserService sysUserService;

    public AdminUserServiceImpl(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 分页查询用户，并批量补充当前页用户的角色信息。
     */
    @Override
    public PageVO<AdminUserVO> listUsers(UserPageQueryDTO queryDTO) {
        // 1. 创建 MyBatis-Plus 分页参数并查询当前页用户
        Page<SysUser> page = new Page<>(queryDTO.page(), queryDTO.pageSize());
        IPage<SysUser> userPage = sysUserService.pageAdminUsers(page, queryDTO);

        // 2. 提取当前页用户 ID，批量查询角色，避免逐个用户查询产生 N+1 问题
        List<Long> userIds = userPage.getRecords().stream()
                .map(SysUser::getId)
                .toList();
        List<UserRoleCodeRow> roleRows =
                sysUserService.findRoleCodesByUserIds(userIds);

        // 3. 按用户 ID 对角色编码进行分组
        Map<Long, List<String>> rolesByUserId = roleRows.stream()
                .collect(Collectors.groupingBy(
                        UserRoleCodeRow::userId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                UserRoleCodeRow::roleCode,
                                Collectors.toList()
                        )
                ));

        // 4. 将数据库实体转换为对外返回的用户信息
        List<AdminUserVO> records = userPage.getRecords().stream()
                .map(user -> toAdminUserVO(
                        user,
                        rolesByUserId.getOrDefault(user.getId(), List.of())
                ))
                .toList();

        // 5. 保留数据库分页查询返回的总数、当前页码和每页数量
        return new PageVO<>(
                records,
                userPage.getTotal(),
                userPage.getCurrent(),
                userPage.getSize()
        );
    }

    /**
     * 将用户实体和角色编码转换为管理员用户视图。
     */
    private AdminUserVO toAdminUserVO(SysUser user, List<String> roles) {
        return new AdminUserVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus(),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
