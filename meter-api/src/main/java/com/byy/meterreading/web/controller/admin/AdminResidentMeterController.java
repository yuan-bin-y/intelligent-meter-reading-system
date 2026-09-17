package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.residentmeter.BindResidentMeterDTO;
import com.byy.meterreading.dto.residentmeter.BindingResourcePageQueryDTO;
import com.byy.meterreading.dto.residentmeter.ResidentMeterPageQueryDTO;
import com.byy.meterreading.service.ResidentMeterService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.residentmeter.MeterBoundResidentVO;
import com.byy.meterreading.vo.residentmeter.ResidentBoundMeterVO;
import com.byy.meterreading.vo.residentmeter.ResidentMeterBindingVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员维护居民与表具绑定关系的接口。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminResidentMeterController {

    private final ResidentMeterService residentMeterService;

    public AdminResidentMeterController(
            ResidentMeterService residentMeterService
    ) {
        this.residentMeterService = residentMeterService;
    }

    @PostMapping("/resident-meters")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ResidentMeterBindingVO> bindMeter(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BindResidentMeterDTO bindDTO
    ) {
        return Result.success(residentMeterService.bindMeter(
                extractUserId(jwt),
                bindDTO
        ));
    }

    @DeleteMapping("/resident-meters/{residentId}/{meterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> unbindMeter(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long residentId,
            @PathVariable Long meterId
    ) {
        residentMeterService.unbindMeter(
                extractUserId(jwt),
                residentId,
                meterId
        );
        return Result.success(null);
    }

    @GetMapping("/resident-meters")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<ResidentMeterBindingVO>> listBindings(
            @Valid @ModelAttribute ResidentMeterPageQueryDTO queryDTO
    ) {
        return Result.success(residentMeterService.listBindings(queryDTO));
    }

    @GetMapping("/residents/{residentId}/meters")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<ResidentBoundMeterVO>> listResidentMeters(
            @PathVariable Long residentId,
            @Valid @ModelAttribute BindingResourcePageQueryDTO queryDTO
    ) {
        return Result.success(residentMeterService.listResidentMeters(
                residentId,
                queryDTO
        ));
    }

    @GetMapping("/meters/{meterId}/residents")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<MeterBoundResidentVO>> listMeterResidents(
            @PathVariable Long meterId,
            @Valid @ModelAttribute BindingResourcePageQueryDTO queryDTO
    ) {
        return Result.success(residentMeterService.listMeterResidents(
                meterId,
                queryDTO
        ));
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
