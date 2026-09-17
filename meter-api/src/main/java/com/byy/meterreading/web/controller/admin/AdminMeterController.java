package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.meter.CreateMeterDTO;
import com.byy.meterreading.service.MeterService;
import com.byy.meterreading.vo.meter.CreateMeterVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员表具管理接口。
 */
@RestController
@RequestMapping("/api/v1/admin/meters")
public class AdminMeterController {

    private final MeterService meterService;

    public AdminMeterController(MeterService meterService) {
        this.meterService = meterService;
    }

    /**
     * 新增表具，创建人和修改人均取自当前管理员身份。
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CreateMeterVO> createMeter(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMeterDTO createMeterDTO
    ) {
        CreateMeterVO createdMeter = meterService.createMeter(
                extractUserId(jwt),
                createMeterDTO
        );
        return Result.success(createdMeter);
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
