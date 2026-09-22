package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.audit.OperationAudit;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.devicemeter.BindDeviceMeterDTO;
import com.byy.meterreading.dto.devicemeter.DeviceMeterPageQueryDTO;
import com.byy.meterreading.dto.devicemeter.DeviceMeterResourcePageQueryDTO;
import com.byy.meterreading.service.DeviceMeterService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.devicemeter.DeviceBoundMeterVO;
import com.byy.meterreading.vo.devicemeter.DeviceMeterBindingVO;
import com.byy.meterreading.vo.devicemeter.MeterBoundDeviceVO;
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
 * 管理员维护设备与表具绑定关系的接口。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminDeviceMeterController {

    private final DeviceMeterService deviceMeterService;

    public AdminDeviceMeterController(DeviceMeterService deviceMeterService) {
        this.deviceMeterService = deviceMeterService;
    }

    @OperationAudit(
            module = "设备表具绑定",
            action = "绑定设备表具",
            resourceType = "DEVICE_METER",
            resourceIdExpression = "#result.data.meterId"
    )
    @PostMapping("/device-meters")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceMeterBindingVO> bindMeter(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BindDeviceMeterDTO bindDTO
    ) {
        return Result.success(deviceMeterService.bindMeter(
                extractUserId(jwt),
                bindDTO
        ));
    }

    @OperationAudit(
            module = "设备表具绑定",
            action = "解绑设备表具",
            resourceType = "DEVICE_METER",
            resourceIdExpression = "#meterId"
    )
    @DeleteMapping("/device-meters/{deviceId}/{meterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> unbindMeter(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long deviceId,
            @PathVariable Long meterId
    ) {
        deviceMeterService.unbindMeter(
                extractUserId(jwt),
                deviceId,
                meterId
        );
        return Result.success(null);
    }

    @GetMapping("/device-meters")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<DeviceMeterBindingVO>> listBindings(
            @Valid @ModelAttribute DeviceMeterPageQueryDTO queryDTO
    ) {
        return Result.success(deviceMeterService.listBindings(queryDTO));
    }

    @GetMapping("/devices/{deviceId}/meters")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<DeviceBoundMeterVO>> listDeviceMeters(
            @PathVariable Long deviceId,
            @Valid @ModelAttribute DeviceMeterResourcePageQueryDTO queryDTO
    ) {
        return Result.success(deviceMeterService.listDeviceMeters(
                deviceId,
                queryDTO
        ));
    }

    @GetMapping("/meters/{meterId}/devices")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<MeterBoundDeviceVO>> listMeterDevices(
            @PathVariable Long meterId,
            @Valid @ModelAttribute DeviceMeterResourcePageQueryDTO queryDTO
    ) {
        return Result.success(deviceMeterService.listMeterDevices(
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
