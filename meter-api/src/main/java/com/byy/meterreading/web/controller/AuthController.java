package com.byy.meterreading.web.controller;

import com.byy.meterreading.auth.service.AuthService;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.auth.ChangePasswordDTO;
import com.byy.meterreading.dto.auth.LoginDTO;
import com.byy.meterreading.dto.auth.RegisterDTO;
import com.byy.meterreading.vo.auth.CurrentUserVO;
import com.byy.meterreading.vo.auth.LoginVO;
import com.byy.meterreading.vo.auth.RegisterVO;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    //用户登录
    @PostMapping("/login")
    public Result<LoginVO> login(
            @Valid @RequestBody LoginDTO loginDTO
    ) {
        LoginVO loginVO = authService.login(loginDTO);
        return Result.success(loginVO);
    }

    /**
     * 注册新用户，注册成功后由客户端继续调用登录接口。
     */
    @PostMapping("/register")
    public Result<RegisterVO> register(
            @Valid @RequestBody RegisterDTO registerDTO
    ) {
        RegisterVO registerVO = authService.register(registerDTO);
        return Result.success(registerVO);
    }

    /**
     * 获取当前登录用户的最新资料和角色。
     */
    @GetMapping("/me")
    public Result<CurrentUserVO> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        CurrentUserVO currentUser =
                authService.getCurrentUser(extractUserId(jwt));
        return Result.success(currentUser);
    }

    /**
     * 修改当前登录用户的密码，用户身份只从已验证的 JWT 中获取。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordDTO changePasswordDTO
    ) {
        authService.changePassword(
                extractUserId(jwt),
                changePasswordDTO
        );
        return Result.success(null);
    }

    /**
     * 从已经通过签名校验的 JWT 中提取用户 ID。
     */
    private Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number userId)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT 中缺少有效的 userId"
            );
        }
        return userId.longValue();
    }
}
