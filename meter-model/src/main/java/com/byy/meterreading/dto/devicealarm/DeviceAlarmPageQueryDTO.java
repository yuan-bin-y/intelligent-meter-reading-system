package com.byy.meterreading.dto.devicealarm;

import com.byy.meterreading.model.enums.DeviceAlarmStatus;
import com.byy.meterreading.model.enums.DeviceAlarmType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 管理员分页查询设备告警的请求参数。
 *
 * <p>所有筛选条件都可以不传；时间采用 ISO-8601 格式，
 * 例如 {@code 2026-09-19T10:30:00}。</p>
 */
public record DeviceAlarmPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize,

        @Size(max = 64, message = "设备编号长度不能超过64个字符")
        String deviceNo,

        @Size(max = 64, message = "设备名称长度不能超过64个字符")
        String deviceName,

        DeviceAlarmType alarmType,

        DeviceAlarmStatus alarmStatus,

        LocalDateTime occurredAtStart,

        LocalDateTime occurredAtEnd
) {

    public DeviceAlarmPageQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        deviceNo = trimToNull(deviceNo);
        deviceName = trimToNull(deviceName);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
