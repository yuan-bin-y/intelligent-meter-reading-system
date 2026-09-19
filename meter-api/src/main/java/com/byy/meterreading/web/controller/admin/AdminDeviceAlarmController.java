package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.devicealarm.DeviceAlarmPageQueryDTO;
import com.byy.meterreading.service.DeviceAlarmService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.devicealarm.DeviceAlarmVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员查询设备告警的接口。
 */
@RestController
@RequestMapping("/api/v1/admin/device-alarms")
public class AdminDeviceAlarmController {

    private final DeviceAlarmService deviceAlarmService;

    public AdminDeviceAlarmController(
            DeviceAlarmService deviceAlarmService
    ) {
        this.deviceAlarmService = deviceAlarmService;
    }

    /**
     * 按设备、告警类型、告警状态和发生时间分页查询告警。
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<DeviceAlarmVO>> listAlarms(
            @Valid @ModelAttribute DeviceAlarmPageQueryDTO queryDTO
    ) {
        return Result.success(deviceAlarmService.listAlarms(queryDTO));
    }

    /**
     * 查询指定告警的完整信息。
     */
    @GetMapping("/{alarmId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceAlarmVO> getAlarm(
            @PathVariable Long alarmId
    ) {
        return Result.success(deviceAlarmService.getAlarm(alarmId));
    }
}
