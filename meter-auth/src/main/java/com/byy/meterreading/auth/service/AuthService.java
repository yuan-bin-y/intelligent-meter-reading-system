package com.byy.meterreading.auth.service;

import com.byy.meterreading.dto.auth.ChangePasswordDTO;
import com.byy.meterreading.dto.auth.LoginDTO;
import com.byy.meterreading.dto.auth.RefreshTokenDTO;
import com.byy.meterreading.dto.auth.RegisterDTO;
import com.byy.meterreading.vo.auth.CurrentUserVO;
import com.byy.meterreading.vo.auth.LoginVO;
import com.byy.meterreading.vo.auth.RefreshTokenVO;
import com.byy.meterreading.vo.auth.RegisterVO;

public interface AuthService {

    /**
     *用户登录
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 注册新用户并为其分配默认的居民角色。
     */
    RegisterVO register(RegisterDTO registerDTO);

    /**
     * 校验并轮换 Refresh Token，返回一组新的 Token。
     */
    RefreshTokenVO refresh(RefreshTokenDTO refreshTokenDTO);

    /**
     * 修改当前登录用户的密码。
     */
    void changePassword(
            Long userId,
            ChangePasswordDTO changePasswordDTO
    );

    /**
     * 根据 JWT 中的用户 ID 获取当前登录用户信息。
     */
    CurrentUserVO getCurrentUser(Long userId);
}
