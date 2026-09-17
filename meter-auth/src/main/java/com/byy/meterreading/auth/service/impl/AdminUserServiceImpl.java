package com.byy.meterreading.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.auth.service.AdminUserService;
import com.byy.meterreading.auth.service.RedisAuthProtectionService;
import com.byy.meterreading.auth.token.RedisAuthSessionService;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.dto.user.AdminCreateUserDTO;
import com.byy.meterreading.dto.user.AdminResetPasswordDTO;
import com.byy.meterreading.dto.user.UpdateUserRolesDTO;
import com.byy.meterreading.dto.user.UpdateUserProfileDTO;
import com.byy.meterreading.dto.user.UpdateUserStatusDTO;
import com.byy.meterreading.dto.user.UserPageQueryDTO;
import com.byy.meterreading.mapper.projection.UserRoleCodeRow;
import com.byy.meterreading.model.SysRole;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.service.SysUserService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.user.AdminUserVO;
import com.byy.meterreading.vo.user.AssignableRoleVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员用户管理业务实现。
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final SysUserService sysUserService;
    private final RedisAuthSessionService redisAuthSessionService;
    private final RedisAuthProtectionService redisAuthProtectionService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(
            SysUserService sysUserService,
            RedisAuthSessionService redisAuthSessionService,
            RedisAuthProtectionService redisAuthProtectionService,
            PasswordEncoder passwordEncoder
    ) {
        this.sysUserService = sysUserService;
        this.redisAuthSessionService = redisAuthSessionService;
        this.redisAuthProtectionService = redisAuthProtectionService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 创建用户、保存 BCrypt 密码哈希并批量绑定初始角色。
     */
    @Override
    @Transactional
    public AdminUserVO createUser(AdminCreateUserDTO adminCreateUserDTO) {
        String username = adminCreateUserDTO.username();

        // 1. 预先检查用户名，数据库唯一索引继续负责并发场景兜底
        if (sysUserService.existsByUsername(username)) {
            throw new ResourceConflictException("用户名已存在");
        }

        // 2. 一次查询并校验管理员指定的全部角色
        List<SysRole> roles = resolveEnabledRoles(adminCreateUserDTO.roles());

        // 3. 明文密码只用于生成 BCrypt 哈希，不写入数据库
        SysUser user = SysUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(
                        adminCreateUserDTO.password()
                ))
                .displayName(adminCreateUserDTO.displayName())
                .status(adminCreateUserDTO.status())
                .build();
        try {
            sysUserService.createUser(user);
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException("用户名已存在", exception);
        }

        // 4. 使用回填的用户 ID 批量写入用户角色关系
        LocalDateTime createdAt = LocalDateTime.now();
        sysUserService.replaceUserRoles(
                user.getId(),
                roles.stream().map(SysRole::getId).toList(),
                createdAt
        );

        // 5. 重新查询数据库默认生成的创建时间和更新时间
        SysUser createdUser = sysUserService.findById(user.getId());
        return toAdminUserVO(
                createdUser,
                adminCreateUserDTO.roles()
        );
    }

    /**
     * 分页查询用户，并批量补充当前页用户的角色信息。
     */
    @Override
    public PageVO<AdminUserVO> listUsers(UserPageQueryDTO queryDTO) {
        // 1. 创建 MyBatis-Plus 分页参数并查询当前页用户
        Page<SysUser> page = new Page<>(queryDTO.page(), queryDTO.pageSize());
        IPage<SysUser> userPage = sysUserService.pageAdminUsers(page, queryDTO);

        // 2. 提取当前页用户 ID，批量查询角色，避免逐个用户查询产生 N+1 问题
        List<Long> userIds = userPage.getRecords().stream()
                .map(SysUser::getId)
                .toList();
        List<UserRoleCodeRow> roleRows =
                sysUserService.findRoleCodesByUserIds(userIds);

        // 3. 按用户 ID 对角色编码进行分组
        Map<Long, List<String>> rolesByUserId = roleRows.stream()
                .collect(Collectors.groupingBy(
                        UserRoleCodeRow::userId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                UserRoleCodeRow::roleCode,
                                Collectors.toList()
                        )
                ));

        // 4. 将数据库实体转换为对外返回的用户信息
        List<AdminUserVO> records = userPage.getRecords().stream()
                .map(user -> toAdminUserVO(
                        user,
                        rolesByUserId.getOrDefault(user.getId(), List.of())
                ))
                .toList();

        // 5. 保留数据库分页查询返回的总数、当前页码和每页数量
        return new PageVO<>(
                records,
                userPage.getTotal(),
                userPage.getCurrent(),
                userPage.getSize()
        );
    }

    /**
     * 查询单个用户的基本信息和角色。
     */
    @Override
    public AdminUserVO getUser(Long userId) {
        // 1. 根据主键查询 sys_user 表中的用户
        SysUser user = sysUserService.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        // 2. 查询用户拥有的有效角色编码
        List<String> roles = sysUserService.findRoleCodesByUserId(userId);

        // 3. 将用户基本信息和角色组装成对外返回的 VO
        return toAdminUserVO(user, roles);
    }

    /**
     * 启用或禁用指定用户，并在状态变化后撤销该用户的全部登录会话。
     */
    @Override
    @Transactional
    public AdminUserVO updateUserStatus(
            Long currentAdminId,
            Long targetUserId,
            UpdateUserStatusDTO updateUserStatusDTO
    ) {
        Integer targetStatus = updateUserStatusDTO.status();

        // 1. 禁止管理员禁用自己的账号，避免失去后台管理入口
        if (currentAdminId.equals(targetUserId)
                && Integer.valueOf(0).equals(targetStatus)) {
            throw new IllegalArgumentException("不能禁用当前登录账号");
        }

        // 2. 查询目标用户，用户不存在时返回 404
        SysUser user = sysUserService.findById(targetUserId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        // 3. 目标状态没有变化时不更新数据库，也不重复清理登录会话
        if (targetStatus.equals(user.getStatus())) {
            List<String> roles =
                    sysUserService.findRoleCodesByUserId(targetUserId);
            return toAdminUserVO(user, roles);
        }

        // 4. 更新用户状态和最后修改时间
        LocalDateTime updatedAt = LocalDateTime.now();
        int updatedRows = sysUserService.updateUserStatus(
                targetUserId,
                targetStatus,
                updatedAt
        );
        if (updatedRows != 1) {
            throw new ResourceNotFoundException("用户不存在");
        }

        // 5. 状态变化后撤销该用户全部设备的 Access Token 和 Refresh Token 会话
        redisAuthSessionService.revokeAll(targetUserId);

        // 6. 更新内存中的实体并组装最新用户信息
        user.setStatus(targetStatus);
        user.setUpdatedAt(updatedAt);
        List<String> roles =
                sysUserService.findRoleCodesByUserId(targetUserId);
        return toAdminUserVO(user, roles);
    }

    /**
     * 重新设置指定用户拥有的角色，并使该用户原有登录会话失效。
     */
    @Override
    @Transactional
    public AdminUserVO updateUserRoles(
            Long currentAdminId,
            Long targetUserId,
            UpdateUserRolesDTO updateUserRolesDTO
    ) {
        List<String> requestedRoleCodes = updateUserRolesDTO.roles();

        // 1. 确认目标用户存在
        SysUser user = sysUserService.findById(targetUserId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        // 2. 禁止当前管理员移除自己的 ADMIN 角色
        if (currentAdminId.equals(targetUserId)
                && !requestedRoleCodes.contains("ADMIN")) {
            throw new IllegalArgumentException(
                    "不能移除当前登录账号的 ADMIN 角色"
            );
        }

        // 3. 批量查询请求中的有效角色，并检查是否存在无效或已禁用的角色编码
        List<SysRole> enabledRoles = resolveEnabledRoles(requestedRoleCodes);

        // 4. 新旧角色相同时不修改数据库，也不清除登录会话
        List<String> currentRoleCodes =
                sysUserService.findRoleCodesByUserId(targetUserId);
        if (Set.copyOf(currentRoleCodes)
                .equals(Set.copyOf(requestedRoleCodes))) {
            return toAdminUserVO(user, currentRoleCodes);
        }

        // 5. 按请求顺序取得角色 ID，并在数据库事务中替换用户角色关系
        List<Long> roleIds = enabledRoles.stream()
                .map(SysRole::getId)
                .toList();
        sysUserService.replaceUserRoles(
                targetUserId,
                roleIds,
                LocalDateTime.now()
        );

        // 6. 权限变化后撤销全部旧会话，旧 JWT 中的角色立即失效
        redisAuthSessionService.revokeAll(targetUserId);

        // 7. DTO 已完成大写转换和去重，可直接作为最新角色返回
        return toAdminUserVO(user, requestedRoleCodes);
    }

    /**
     * 修改用户显示名称。
     */
    @Override
    @Transactional
    public AdminUserVO updateUserProfile(
            Long targetUserId,
            UpdateUserProfileDTO updateUserProfileDTO
    ) {
        SysUser user = requireUser(targetUserId);
        String displayName = updateUserProfileDTO.displayName();

        if (!displayName.equals(user.getDisplayName())) {
            LocalDateTime updatedAt = LocalDateTime.now();
            int updatedRows = sysUserService.updateDisplayName(
                    targetUserId,
                    displayName,
                    updatedAt
            );
            if (updatedRows != 1) {
                throw new ResourceNotFoundException("用户不存在");
            }
            user.setDisplayName(displayName);
            user.setUpdatedAt(updatedAt);
        }

        return toAdminUserVO(
                user,
                sysUserService.findRoleCodesByUserId(targetUserId)
        );
    }

    /**
     * 重置用户密码并撤销该用户全部登录会话。
     */
    @Override
    @Transactional
    public void resetUserPassword(
            Long targetUserId,
            AdminResetPasswordDTO adminResetPasswordDTO
    ) {
        requireUser(targetUserId);

        String passwordHash = passwordEncoder.encode(
                adminResetPasswordDTO.newPassword()
        );
        int updatedRows = sysUserService.updatePasswordHashByAdmin(
                targetUserId,
                passwordHash,
                LocalDateTime.now()
        );
        if (updatedRows != 1) {
            throw new ResourceNotFoundException("用户不存在");
        }

        // Redis 失败会抛出异常，使本次 MySQL 事务回滚
        redisAuthSessionService.revokeAll(targetUserId);
    }

    /**
     * 查询管理员可以分配的全部启用角色。
     */
    @Override
    public List<AssignableRoleVO> listAssignableRoles() {
        return sysUserService.findAllEnabledRoles().stream()
                .map(role -> new AssignableRoleVO(
                        role.getId(),
                        role.getRoleCode(),
                        role.getRoleName()
                ))
                .toList();
    }

    /**
     * 清除用户的登录失败计数和临时锁定状态。
     */
    @Override
    public void clearLoginLock(Long targetUserId) {
        SysUser user = requireUser(targetUserId);
        redisAuthProtectionService.clearLoginLock(user.getUsername());
    }

    /**
     * 查询用户，不存在时统一返回资源不存在。
     */
    private SysUser requireUser(Long userId) {
        SysUser user = sysUserService.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        return user;
    }

    /**
     * 校验角色编码全部存在且已启用，并按请求顺序返回角色实体。
     */
    private List<SysRole> resolveEnabledRoles(List<String> requestedRoleCodes) {
        List<SysRole> enabledRoles =
                sysUserService.findEnabledRolesByCodes(requestedRoleCodes);
        Map<String, SysRole> roleByCode = enabledRoles.stream()
                .collect(Collectors.toMap(
                        SysRole::getRoleCode,
                        role -> role
                ));

        List<String> invalidRoleCodes = requestedRoleCodes.stream()
                .filter(roleCode -> !roleByCode.containsKey(roleCode))
                .toList();
        if (!invalidRoleCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "角色不存在或已禁用：" + String.join(", ", invalidRoleCodes)
            );
        }

        return requestedRoleCodes.stream()
                .map(roleByCode::get)
                .toList();
    }

    /**
     * 将用户实体和角色编码转换为管理员用户视图。
     */
    private AdminUserVO toAdminUserVO(SysUser user, List<String> roles) {
        return new AdminUserVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus(),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
