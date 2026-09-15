package com.byy.meterreading.auth.service.impl;

import com.byy.meterreading.auth.security.CustomUserDetails;
import com.byy.meterreading.auth.service.AuthService;
import com.byy.meterreading.auth.token.JwtTokenService;
import com.byy.meterreading.dto.auth.ChangePasswordDTO;
import com.byy.meterreading.dto.auth.LoginDTO;
import com.byy.meterreading.dto.auth.RegisterDTO;
import com.byy.meterreading.model.SysRole;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.service.SysUserService;
import com.byy.meterreading.vo.auth.CurrentUserVO;
import com.byy.meterreading.vo.auth.LoginVO;
import com.byy.meterreading.vo.auth.RegisterVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_REGISTER_ROLE = "RESIDENT";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtTokenService jwtTokenService,
                           SysUserService sysUserService,
                           PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
    }

    //用户登录
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

    // 注册用户并绑定默认的居民角色
    @Override
    @Transactional
    public RegisterVO register(RegisterDTO registerDTO) {
        // 1. 清理用户名和显示名称两端的空格，密码保持用户原始输入
        String username = registerDTO.username().trim();
        String displayName = registerDTO.displayName().trim();

        // 2. 注册前检查用户名，避免正常情况下触发数据库唯一索引异常
        if (sysUserService.existsByUsername(username)) {
            throw new DuplicateKeyException("用户名已存在");
        }

        // 3. 使用 BCrypt 加密明文密码，数据库中只保存密码哈希
        String passwordHash = passwordEncoder.encode(registerDTO.password());

        // 4. 创建启用状态的用户，创建时间由数据库默认值生成
        SysUser user = SysUser.builder()
                .username(username)
                .passwordHash(passwordHash)
                .displayName(displayName)
                .status(1)
                .build();

        try {
            sysUserService.createUser(user);
        } catch (DuplicateKeyException exception) {
            // 两个同名注册请求可能同时通过预查询，最终由数据库唯一索引兜底
            throw new DuplicateKeyException("用户名已存在", exception);
        }

        // 5. 查询系统预置且处于启用状态的居民角色
        SysRole residentRole =
                sysUserService.findEnabledRoleByCode(DEFAULT_REGISTER_ROLE);
        if (residentRole == null) {
            throw new IllegalStateException("系统缺少启用的 RESIDENT 角色");
        }

        // 6. 使用数据库回填的用户 ID 建立用户与居民角色的关联
        sysUserService.bindRole(user.getId(), residentRole.getId());

        // 7. 统一组装注册成功响应；注册完成后仍需调用登录接口获取 JWT
        return new RegisterVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                List.of(DEFAULT_REGISTER_ROLE)
        );
    }

    // 修改当前登录用户的密码
    @Override
    @Transactional
    public void changePassword(
            Long userId,
            ChangePasswordDTO changePasswordDTO
    ) {
        // 1. 根据 JWT 中的用户 ID 查询最新用户数据
        SysUser user = sysUserService.findById(userId);

        // 2. 用户不存在或已经禁用时，使当前登录状态失效
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new AuthenticationCredentialsNotFoundException(
                    "当前登录用户不存在或已被禁用"
            );
        }

        // 3. 使用 BCrypt 校验用户提交的原密码
        if (!passwordEncoder.matches(
                changePasswordDTO.oldPassword(),
                user.getPasswordHash()
        )) {
            throw new IllegalArgumentException("原密码错误");
        }

        // 4. 新密码不能与当前密码相同；BCrypt 哈希不能直接比较字符串
        if (passwordEncoder.matches(
                changePasswordDTO.newPassword(),
                user.getPasswordHash()
        )) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }

        // 5. 为新密码生成新的 BCrypt 哈希
        String newPasswordHash =
                passwordEncoder.encode(changePasswordDTO.newPassword());
        LocalDateTime changedAt = LocalDateTime.now();

        // 6. 只更新仍处于启用状态的当前用户
        int updatedRows = sysUserService.updatePasswordHash(
                userId,
                newPasswordHash,
                changedAt
        );

        // 7. 更新期间用户被删除或禁用时，使当前登录状态失效
        if (updatedRows != 1) {
            throw new AuthenticationCredentialsNotFoundException(
                    "当前登录用户不存在或已被禁用"
            );
        }
    }

    //获取用户身份
    @Override
    public CurrentUserVO getCurrentUser(Long userId) {
        // 1. 根据 JWT 中的用户 ID 查询数据库中的最新用户信息
        SysUser user = sysUserService.findById(userId);

        // 2. 用户不存在或已被禁用时，使当前登录状态失效
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new AuthenticationCredentialsNotFoundException(
                    "当前登录用户不存在或已被禁用"
            );
        }

        // 3. 查询当前仍处于启用状态的角色编码
        List<String> roles =
                sysUserService.findRoleCodesByUserId(user.getId());

        // 4. 组装当前登录用户信息并返回
        return new CurrentUserVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                roles
        );
    }
}
