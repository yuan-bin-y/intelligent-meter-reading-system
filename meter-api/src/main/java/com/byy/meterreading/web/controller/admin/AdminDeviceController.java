package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.device.CreateDeviceDTO;
import com.byy.meterreading.dto.device.DevicePageQueryDTO;
import com.byy.meterreading.dto.device.DeviceVersionDTO;
import com.byy.meterreading.dto.device.ResetDeviceSecretDTO;
import com.byy.meterreading.dto.device.UpdateDeviceDTO;
import com.byy.meterreading.service.DeviceService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.device.CreateDeviceVO;
import com.byy.meterreading.vo.device.DeviceDetailVO;
import com.byy.meterreading.vo.device.DeviceListItemVO;
import com.byy.meterreading.vo.device.DeviceRuntimeStatusVO;
import com.byy.meterreading.vo.device.DeviceVersionVO;
import com.byy.meterreading.vo.device.ResetDeviceSecretVO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员设备档案管理接口。
 */
@RestController
@RequestMapping("/api/v1/admin/devices")
public class AdminDeviceController {

    private final DeviceService deviceService;

    public AdminDeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CreateDeviceVO> createDevice(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateDeviceDTO createDTO
    ) {
        return Result.success(deviceService.createDevice(
                extractUserId(jwt),
                createDTO
        ));
    }

    @PutMapping("/{deviceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceVersionVO> updateDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long deviceId,
            @Valid @RequestBody UpdateDeviceDTO updateDTO
    ) {
        return Result.success(deviceService.updateDevice(
                extractUserId(jwt),
                deviceId,
                updateDTO
        ));
    }

    @GetMapping("/{deviceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceDetailVO> getDevice(@PathVariable Long deviceId) {
        return Result.success(deviceService.getDevice(deviceId));
    }

    /**
     * 查询设备当前是否存在有效心跳。
     */
    @GetMapping("/{deviceId}/runtime-status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceRuntimeStatusVO> getRuntimeStatus(
            @PathVariable Long deviceId
    ) {
        return Result.success(deviceService.getRuntimeStatus(deviceId));
    }

    @PostMapping("/{deviceId}/secret/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ResetDeviceSecretVO> resetDeviceSecret(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long deviceId,
            @Valid @RequestBody ResetDeviceSecretDTO resetDTO
    ) {
        return Result.success(deviceService.resetDeviceSecret(
                extractUserId(jwt),
                deviceId,
                resetDTO
        ));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<DeviceListItemVO>> listDevices(
            @Valid @ModelAttribute DevicePageQueryDTO queryDTO
    ) {
        return Result.success(deviceService.listDevices(queryDTO));
    }

    @PutMapping("/{deviceId}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceVersionVO> enableDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long deviceId,
            @Valid @RequestBody DeviceVersionDTO versionDTO
    ) {
        return Result.success(deviceService.enableDevice(
                extractUserId(jwt),
                deviceId,
                versionDTO
        ));
    }

    @PutMapping("/{deviceId}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceVersionVO> disableDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long deviceId,
            @Valid @RequestBody DeviceVersionDTO versionDTO
    ) {
        return Result.success(deviceService.disableDevice(
                extractUserId(jwt),
                deviceId,
                versionDTO
        ));
    }

    @DeleteMapping("/{deviceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long deviceId,
            @RequestParam Integer version
    ) {
        deviceService.deleteDevice(
                extractUserId(jwt),
                deviceId,
                version
        );
        return Result.success(null);
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
