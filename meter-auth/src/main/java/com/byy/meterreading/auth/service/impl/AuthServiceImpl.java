package com.byy.meterreading.auth.service.impl;

import com.byy.meterreading.auth.security.CustomUserDetails;
import com.byy.meterreading.auth.service.AuthService;
import com.byy.meterreading.auth.service.RedisAuthProtectionService;
import com.byy.meterreading.auth.token.IssuedTokenPair;
import com.byy.meterreading.auth.token.JwtTokenService;
import com.byy.meterreading.auth.token.RedisAuthSessionService;
import com.byy.meterreading.dto.auth.ChangePasswordDTO;
import com.byy.meterreading.dto.auth.LoginDTO;
import com.byy.meterreading.dto.auth.RefreshTokenDTO;
import com.byy.meterreading.dto.auth.RegisterDTO;
import com.byy.meterreading.model.SysRole;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.service.SysUserService;
import com.byy.meterreading.vo.auth.CurrentUserVO;
import com.byy.meterreading.vo.auth.LoginVO;
import com.byy.meterreading.vo.auth.RefreshTokenVO;
import com.byy.meterreading.vo.auth.RegisterVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_REGISTER_ROLE = "RESIDENT";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final JwtDecoder refreshTokenDecoder;
    private final RedisAuthSessionService redisAuthSessionService;
    private final RedisAuthProtectionService redisAuthProtectionService;
    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtTokenService jwtTokenService,
                           @Qualifier("refreshTokenDecoder")
                           JwtDecoder refreshTokenDecoder,
                           RedisAuthSessionService redisAuthSessionService,
                           RedisAuthProtectionService redisAuthProtectionService,
                           SysUserService sysUserService,
                           PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.redisAuthSessionService = redisAuthSessionService;
        this.redisAuthProtectionService = redisAuthProtectionService;
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
    }

    //用户登录
    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 1. 接收 LoginDTO

        // 2. 认证前检查该用户名是否已因连续失败而被临时锁定
        redisAuthProtectionService.checkLoginAllowed(loginDTO.username());

        // 3. 调用 AuthenticationManager 认证，密码错误时累计失败次数
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            loginDTO.username(),
                            loginDTO.password()
                    )
            );
        } catch (BadCredentialsException exception) {
            redisAuthProtectionService.recordLoginFailure(
                    loginDTO.username()
            );
            throw exception;
        }

        // 4. 认证成功后清除该用户名尚未达到锁定阈值的失败记录
        redisAuthProtectionService.clearLoginFailures(loginDTO.username());

        // 5. 从认证结果中获取 Principal，判断类型后转成 CustomUserDetails
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails)) {
            throw new IllegalStateException("认证结果中的用户信息类型不正确");
        }
        CustomUserDetails userDetails = (CustomUserDetails) principal;

        // 6. 准备返回所需的用户数据
        Long userId = userDetails.getUserId();
        String username = userDetails.getUsername();
        String displayName = userDetails.getDisplayName();
        List<String> roles = userDetails.getRoles();

        // 7. 为本次登录签发属于同一个 sid 的 Access Token 和 Refresh Token
        IssuedTokenPair tokenPair = jwtTokenService.issue(userDetails);

        // 8. Redis 会话需要覆盖完整的 Refresh Token 有效期
        redisAuthSessionService.activate(
                userId,
                tokenPair.sessionId(),
                tokenPair.refreshTokenId(),
                Duration.ofSeconds(tokenPair.refreshExpiresIn())
        );

        // 9. 统一组装包含双 Token 的 LoginVO 并返回
        return new LoginVO(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                "Bearer",
                tokenPair.accessExpiresIn(),
                tokenPair.refreshExpiresIn(),
                userId,
                username,
                displayName,
                roles
        );
    }

    // 校验并轮换 Refresh Token
    @Override
    public RefreshTokenVO refresh(RefreshTokenDTO refreshTokenDTO) {
        // 1. 使用 Refresh Token 专用 Decoder 校验签名、时间、issuer 和 Token 类型
        Jwt refreshJwt;
        try {
            refreshJwt = refreshTokenDecoder.decode(
                    refreshTokenDTO.refreshToken()
            );
        } catch (JwtException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Refresh Token 无效或已过期",
                    exception
            );
        }

        // 2. 从验证通过的 Refresh Token 中获取用户、会话和旧 Token 标识
        Object userIdClaim = refreshJwt.getClaim("userId");
        String sessionId = refreshJwt.getClaimAsString(
                JwtTokenService.CLAIM_SESSION_ID
        );
        String oldRefreshTokenId = refreshJwt.getId();
        if (!(userIdClaim instanceof Number userId)
                || sessionId == null
                || sessionId.isBlank()
                || oldRefreshTokenId == null
                || oldRefreshTokenId.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Refresh Token 缺少有效的会话信息"
            );
        }

        Long currentUserId = userId.longValue();

        // 3. 查询数据库中的最新用户，账号不存在或禁用时拒绝续期
        SysUser user = sysUserService.findById(currentUserId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            redisAuthSessionService.revoke(currentUserId, sessionId);
            throw new AuthenticationCredentialsNotFoundException(
                    "当前登录用户不存在或已被禁用"
            );
        }

        // 4. 重新查询角色，使新 Access Token 使用数据库中的最新权限
        List<String> roles =
                sysUserService.findRoleCodesByUserId(currentUserId);
        CustomUserDetails userDetails = new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                null,
                user.getDisplayName(),
                user.getStatus(),
                roles
        );

        // 5. 保留当前 sid，生成新的 Access Token 和 Refresh Token
        IssuedTokenPair tokenPair =
                jwtTokenService.rotate(userDetails, sessionId);

        // 6. 原子比较旧 refreshJti 并替换为新值，同时刷新会话有效期
        boolean rotated = redisAuthSessionService.rotateRefreshToken(
                currentUserId,
                sessionId,
                oldRefreshTokenId,
                tokenPair.refreshTokenId(),
                Duration.ofSeconds(tokenPair.refreshExpiresIn())
        );

        // 7. 旧 Refresh Token 被重复使用时撤销整个当前登录会话
        if (!rotated) {
            redisAuthSessionService.revoke(currentUserId, sessionId);
            throw new AuthenticationCredentialsNotFoundException(
                    "Refresh Token 已失效，请重新登录"
            );
        }

        // 8. 返回轮换后的一组新 Token
        return new RefreshTokenVO(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                "Bearer",
                tokenPair.accessExpiresIn(),
                tokenPair.refreshExpiresIn()
        );
    }

    // 退出当前设备对应的整个登录会话
    @Override
    public void logout(Long userId, String sessionId) {
        redisAuthSessionService.revoke(userId, sessionId);
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

        // 8. 密码修改成功后清除该用户全部设备的登录会话
        redisAuthSessionService.revokeAll(userId);
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
