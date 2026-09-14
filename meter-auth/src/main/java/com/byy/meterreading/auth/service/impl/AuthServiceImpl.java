package com.byy.meterreading.auth.service.impl;

import com.byy.meterreading.auth.security.CustomUserDetails;
import com.byy.meterreading.auth.service.AuthService;
import com.byy.meterreading.auth.token.JwtTokenService;
import com.byy.meterreading.dto.auth.LoginDTO;
import com.byy.meterreading.vo.auth.LoginVO;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 1. 接收 LoginDTO

        // 2. 调用 AuthenticationManager 进行认证
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        loginDTO.username(),
                        loginDTO.password()
                )
        );

        // 3. 从认证结果中获取 Principal，判断类型后转成 CustomUserDetails
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails)) {
            throw new IllegalStateException("认证结果中的用户信息类型不正确");
        }
        CustomUserDetails userDetails = (CustomUserDetails) principal;

        // 4. 准备返回所需的用户数据
        Long userId = userDetails.getUserId();
        String username = userDetails.getUsername();
        String displayName = userDetails.getDisplayName();
        List<String> roles = userDetails.getRoles();

        // 5. 调用 JwtTokenService 签发 JWT
        String accessToken = jwtTokenService.generate(userDetails);
        long expiresIn = jwtTokenService.getExpiresIn();

        // 6. 统一组装 LoginVO 并返回
        return new LoginVO(
                accessToken,
                "Bearer",
                expiresIn,
                userId,
                username,
                displayName,
                roles
        );
    }
}
