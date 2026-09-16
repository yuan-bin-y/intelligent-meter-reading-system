package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.auth.service.AdminUserService;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.user.UserPageQueryDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.user.AdminUserVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
     * 按用户名、状态和角色筛选并分页查询用户。
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<AdminUserVO>> listUsers(
            @Valid @ModelAttribute UserPageQueryDTO queryDTO
    ) {
        return Result.success(adminUserService.listUsers(queryDTO));
    }
}
