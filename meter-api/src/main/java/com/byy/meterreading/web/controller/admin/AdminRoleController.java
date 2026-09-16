package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.auth.service.AdminUserService;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.vo.user.AssignableRoleVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员角色选择接口。
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
public class AdminRoleController {

    private final AdminUserService adminUserService;

    public AdminRoleController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 查询创建用户和分配角色时可以选择的全部启用角色。
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<AssignableRoleVO>> listAssignableRoles() {
        return Result.success(adminUserService.listAssignableRoles());
    }
}
