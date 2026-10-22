package com.byy.meterreading.dto.device;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 管理员重置设备密钥时提交的乐观锁版本。
 */
public record ResetDeviceSecretDTO(
        @NotNull(message = "数据版本不能为空")
        @Min(value = 0, message = "数据版本不能小于0")
        Integer version,
        @NotBlank(message = "请求ID不能为空")
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = "请求ID必须为UUID")
        String requestId
) {
}
