package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.meter.CreateMeterDTO;
import com.byy.meterreading.dto.meter.MeterPageQueryDTO;
import com.byy.meterreading.dto.meter.UpdateMeterDTO;
import com.byy.meterreading.dto.meter.UpdateMeterStatusDTO;
import com.byy.meterreading.service.MeterService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meter.CreateMeterVO;
import com.byy.meterreading.vo.meter.MeterDetailVO;
import com.byy.meterreading.vo.meter.MeterListItemVO;
import com.byy.meterreading.vo.meter.MeterVersionVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * 按关键字、类型、显示方式和状态分页查询表具。
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<MeterListItemVO>> listMeters(
            @Valid @ModelAttribute MeterPageQueryDTO queryDTO
    ) {
        return Result.success(meterService.listMeters(queryDTO));
    }

    /**
     * 查询指定表具的完整档案。
     */
    @GetMapping("/{meterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MeterDetailVO> getMeter(@PathVariable Long meterId) {
        return Result.success(meterService.getMeter(meterId));
    }

    /**
     * 修改表具基础资料，表具编号和状态不在此接口中修改。
     */
    @PutMapping("/{meterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MeterVersionVO> updateMeter(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long meterId,
            @Valid @RequestBody UpdateMeterDTO updateMeterDTO
    ) {
        return Result.success(meterService.updateMeter(
                extractUserId(jwt),
                meterId,
                updateMeterDTO
        ));
    }

    /**
     * 按表具生命周期规则修改状态。
     */
    @PutMapping("/{meterId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MeterVersionVO> updateMeterStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long meterId,
            @Valid @RequestBody UpdateMeterStatusDTO updateMeterStatusDTO
    ) {
        return Result.success(meterService.updateMeterStatus(
                extractUserId(jwt),
                meterId,
                updateMeterStatusDTO
        ));
    }

    /**
     * 逻辑删除停用状态且版本未过期的表具。
     */
    @DeleteMapping("/{meterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteMeter(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long meterId,
            @RequestParam Integer version
    ) {
        meterService.deleteMeter(extractUserId(jwt), meterId, version);
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
