package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byy.meterreading.mapper.SysRoleMapper;
import com.byy.meterreading.mapper.SysUserMapper;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.service.SysUserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    public SysUserServiceImpl(SysUserMapper sysUserMapper,
                              SysRoleMapper sysRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
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

    //更具id查询角色编码
    @Override
    public List<String> findRoleCodesByUserId(Long userId) {
        List<String> roleCodes =
                sysRoleMapper.selectRoleCodesByUserId(userId);

        return roleCodes == null ? List.of() : roleCodes;
    }
}
