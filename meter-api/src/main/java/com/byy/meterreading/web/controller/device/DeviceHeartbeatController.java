package com.byy.meterreading.web.controller.device;

import com.byy.meterreading.auth.device.DevicePrincipal;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.service.DeviceHeartbeatService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备心跳接口。
 */
@RestController
@RequestMapping("/api/v1/device")
public class DeviceHeartbeatController {

    private final DeviceHeartbeatService deviceHeartbeatService;

    public DeviceHeartbeatController(
            DeviceHeartbeatService deviceHeartbeatService
    ) {
        this.deviceHeartbeatService = deviceHeartbeatService;
    }

    /**
     * 记录当前认证设备的心跳并刷新 Redis 在线状态 TTL。
     */
    @PostMapping("/heartbeat")
    @PreAuthorize("hasRole('DEVICE')")
    public Result<Void> heartbeat(
            @AuthenticationPrincipal DevicePrincipal principal
    ) {
        deviceHeartbeatService.heartbeat(
                principal.deviceId(),
                principal.deviceNo(),
                principal.credentialVersion()
        );
        return Result.success(null);
    }
}
