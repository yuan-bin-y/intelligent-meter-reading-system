package com.byy.meterreading.dto.devicemeter;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 管理员为设备绑定表具的请求参数。
 */
public record BindDeviceMeterDTO(
        @NotNull(message = "设备ID不能为空")
        @Positive(message = "设备ID必须大于0")
        Long deviceId,

        @NotNull(message = "表具ID不能为空")
        @Positive(message = "表具ID必须大于0")
        Long meterId
) {
}
