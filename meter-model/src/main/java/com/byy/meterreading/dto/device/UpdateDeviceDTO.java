package com.byy.meterreading.dto.device;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改设备资料请求；设备编号和设备类型创建后不可修改。
 */
public record UpdateDeviceDTO(
        @NotBlank(message = "设备名称不能为空")
        @Size(max = 64, message = "设备名称长度不能超过64个字符")
        String deviceName,

        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark,

        @NotNull(message = "数据版本不能为空")
        @Min(value = 0, message = "数据版本不能小于0")
        Integer version
) {
}
