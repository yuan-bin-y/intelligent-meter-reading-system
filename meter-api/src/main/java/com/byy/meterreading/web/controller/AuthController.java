package com.byy.meterreading.web.controller;

import com.byy.meterreading.auth.service.AuthService;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.auth.LoginDTO;
import com.byy.meterreading.vo.auth.CurrentUserVO;
import com.byy.meterreading.vo.auth.LoginVO;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(
            @Valid @RequestBody LoginDTO loginDTO
    ) {
        LoginVO loginVO = authService.login(loginDTO);
        return Result.success(loginVO);
    }

    /**
     * 获取当前登录用户的最新资料和角色。
     */
    @GetMapping("/me")
    public Result<CurrentUserVO> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        // userId 必须从已验证的 JWT 中获取，不能接收客户端传值。
        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number userId)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT 中缺少有效的 userId"
            );
        }

        CurrentUserVO currentUser =
                authService.getCurrentUser(userId.longValue());
        return Result.success(currentUser);
    }
}
