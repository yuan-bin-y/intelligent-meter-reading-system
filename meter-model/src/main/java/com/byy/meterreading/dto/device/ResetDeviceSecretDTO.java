package com.byy.meterreading.dto.device;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员重置设备密钥时提交的乐观锁版本。
 */
public record ResetDeviceSecretDTO(
        @NotNull(message = "数据版本不能为空")
        @Min(value = 0, message = "数据版本不能小于0")
        Integer version
) {
}
