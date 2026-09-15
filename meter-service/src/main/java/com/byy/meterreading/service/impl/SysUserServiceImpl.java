package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.byy.meterreading.mapper.SysRoleMapper;
import com.byy.meterreading.mapper.SysUserMapper;
import com.byy.meterreading.mapper.SysUserRoleMapper;
import com.byy.meterreading.model.SysRole;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.model.SysUserRole;
import com.byy.meterreading.service.SysUserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public SysUserServiceImpl(SysUserMapper sysUserMapper,
                              SysRoleMapper sysRoleMapper,
                              SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    // 根据用户 ID 查询用户，使用 MyBatis-Plus 单表主键查询
    @Override
    public SysUser findById(Long userId) {
        return sysUserMapper.selectById(userId);
    }

    //查用户名
    @Override
    public SysUser findByUsername(String username) {
        LambdaQueryWrapper<SysUser> queryWrapper =
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username);

        return sysUserMapper.selectOne(queryWrapper);
    }

    // 使用 COUNT 查询判断用户名是否存在，不需要加载完整用户数据
    @Override
    public boolean existsByUsername(String username) {
        LambdaQueryWrapper<SysUser> queryWrapper =
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username);

        return sysUserMapper.selectCount(queryWrapper) > 0;
    }

    // 插入用户后，MyBatis-Plus 会把自增主键回填到 user.id
    @Override
    public void createUser(SysUser user) {
        sysUserMapper.insert(user);
    }

    // 注册时只能绑定处于启用状态的角色
    @Override
    public SysRole findEnabledRoleByCode(String roleCode) {
        LambdaQueryWrapper<SysRole> queryWrapper =
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, roleCode)
                        .eq(SysRole::getStatus, 1);

        return sysRoleMapper.selectOne(queryWrapper);
    }

    // 向用户角色关联表写入一条绑定记录
    @Override
    public void bindRole(Long userId, Long roleId) {
        SysUserRole userRole = SysUserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .build();
        sysUserRoleMapper.insert(userRole);
    }

    // 只允许更新启用用户，并返回数据库实际更新的行数
    @Override
    public int updatePasswordHash(
            Long userId,
            String passwordHash,
            LocalDateTime updatedAt
    ) {
        LambdaUpdateWrapper<SysUser> updateWrapper =
                new LambdaUpdateWrapper<SysUser>()
                        .eq(SysUser::getId, userId)
                        .eq(SysUser::getStatus, 1)
                        .set(SysUser::getPasswordHash, passwordHash)
                        .set(SysUser::getUpdatedAt, updatedAt);

        return sysUserMapper.update(updateWrapper);
    }

    //更具id查询角色编码
    @Override
    public List<String> findRoleCodesByUserId(Long userId) {
        List<String> roleCodes =
                sysRoleMapper.selectRoleCodesByUserId(userId);

        return roleCodes == null ? List.of() : roleCodes;
    }
}
