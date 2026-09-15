package com.byy.meterreading.auth.service;

import com.byy.meterreading.dto.auth.LoginDTO;
import com.byy.meterreading.vo.auth.CurrentUserVO;
import com.byy.meterreading.vo.auth.LoginVO;

public interface AuthService {

    LoginVO login(LoginDTO loginDTO);

    /**
     * 根据 JWT 中的用户 ID 获取当前登录用户信息。
     */
    CurrentUserVO getCurrentUser(Long userId);
}
