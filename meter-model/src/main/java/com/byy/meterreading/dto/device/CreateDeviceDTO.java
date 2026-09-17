package com.byy.meterreading.dto.device;

import com.byy.meterreading.model.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员新增采集设备请求。
 */
public record CreateDeviceDTO(
        @NotBlank(message = "设备编号不能为空")
        @Size(max = 64, message = "设备编号长度不能超过64个字符")
        String deviceNo,

        @NotBlank(message = "设备名称不能为空")
        @Size(max = 64, message = "设备名称长度不能超过64个字符")
        String deviceName,

        @NotNull(message = "设备类型不能为空")
        DeviceType deviceType,

        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
}
