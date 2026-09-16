package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.auth.service.AdminUserService;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.user.AdminCreateUserDTO;
import com.byy.meterreading.dto.user.AdminResetPasswordDTO;
import com.byy.meterreading.dto.user.UpdateUserRolesDTO;
import com.byy.meterreading.dto.user.UpdateUserProfileDTO;
import com.byy.meterreading.dto.user.UpdateUserStatusDTO;
import com.byy.meterreading.dto.user.UserPageQueryDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.user.AdminUserVO;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理接口。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 创建用户并分配初始角色。
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AdminUserVO> createUser(
            @Valid @RequestBody AdminCreateUserDTO adminCreateUserDTO
    ) {
        return Result.success(adminUserService.createUser(adminCreateUserDTO));
    }

    /**
     * 按用户名、状态和角色筛选并分页查询用户。
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<AdminUserVO>> listUsers(
            @Valid @ModelAttribute UserPageQueryDTO queryDTO
    ) {
        return Result.success(adminUserService.listUsers(queryDTO));
    }

    /**
     * 根据用户 ID 查询用户详情。
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AdminUserVO> getUser(@PathVariable Long userId) {
        return Result.success(adminUserService.getUser(userId));
    }

    /**
     * 启用或禁用指定用户。
     */
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AdminUserVO> updateUserStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusDTO updateUserStatusDTO
    ) {
        return Result.success(adminUserService.updateUserStatus(
                extractUserId(jwt),
                userId,
                updateUserStatusDTO
        ));
    }

    /**
     * 修改指定用户拥有的角色。
     */
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AdminUserVO> updateUserRoles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRolesDTO updateUserRolesDTO
    ) {
        return Result.success(adminUserService.updateUserRoles(
                extractUserId(jwt),
                userId,
                updateUserRolesDTO
        ));
    }

    /**
     * 修改指定用户的显示名称。
     */
    @PutMapping("/{userId}/profile")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AdminUserVO> updateUserProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserProfileDTO updateUserProfileDTO
    ) {
        return Result.success(adminUserService.updateUserProfile(
                userId,
                updateUserProfileDTO
        ));
    }

    /**
     * 重置指定用户的登录密码，并使其全部登录会话失效。
     */
    @PutMapping("/{userId}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetUserPassword(
            @PathVariable Long userId,
            @Valid @RequestBody AdminResetPasswordDTO adminResetPasswordDTO
    ) {
        adminUserService.resetUserPassword(userId, adminResetPasswordDTO);
        return Result.success(null);
    }

    /**
     * 清除指定用户的登录失败次数和临时锁定状态。
     */
    @DeleteMapping("/{userId}/login-lock")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> clearLoginLock(@PathVariable Long userId) {
        adminUserService.clearLoginLock(userId);
        return Result.success(null);
    }

    /**
     * 从已经通过认证的 JWT 中提取当前管理员 ID。
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
