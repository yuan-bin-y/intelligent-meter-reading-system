package com.byy.meterreading.auth.security;

import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.service.SysUserService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserService sysUserService;

    public CustomUserDetailsService(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Override
    public CustomUserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        // 1. 根据用户名查询用户
        SysUser user = sysUserService.findByUsername(username);

        // 2. 用户不存在时终止认证
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        // 3. 根据用户 ID 查询角色编码
        List<String> roleCodes =
                sysUserService.findRoleCodesByUserId(user.getId());

        // 4. 组装并返回 Spring Security 使用的当前用户
        return new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getDisplayName(),
                user.getStatus(),
                roleCodes
        );
    }
}
