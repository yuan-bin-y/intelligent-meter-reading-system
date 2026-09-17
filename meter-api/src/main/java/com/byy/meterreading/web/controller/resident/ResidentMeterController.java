package com.byy.meterreading.web.controller.resident;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.residentmeter.BindingResourcePageQueryDTO;
import com.byy.meterreading.service.ResidentMeterService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.residentmeter.ResidentBoundMeterVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 居民查询本人绑定表具的接口。
 */
@RestController
@RequestMapping("/api/v1/resident/meters")
public class ResidentMeterController {

    private final ResidentMeterService residentMeterService;

    public ResidentMeterController(
            ResidentMeterService residentMeterService
    ) {
        this.residentMeterService = residentMeterService;
    }

    /**
     * residentId 始终从 JWT 提取，前端不能指定其他居民 ID。
     */
    @GetMapping
    @PreAuthorize("hasRole('RESIDENT')")
    public Result<PageVO<ResidentBoundMeterVO>> listMyMeters(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute BindingResourcePageQueryDTO queryDTO
    ) {
        return Result.success(
                residentMeterService.listCurrentResidentMeters(
                        extractUserId(jwt),
                        queryDTO
                )
        );
    }

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
