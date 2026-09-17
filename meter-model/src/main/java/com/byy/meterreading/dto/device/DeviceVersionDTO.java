package com.byy.meterreading.dto.device;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 启用、停用设备时提交的乐观锁版本。
 */
public record DeviceVersionDTO(
        @NotNull(message = "数据版本不能为空")
        @Min(value = 0, message = "数据版本不能小于0")
        Integer version
) {
}
