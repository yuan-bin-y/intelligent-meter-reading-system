package com.byy.meterreading.dto.residentmeter;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 管理员为居民绑定表具的请求参数。
 */
public record BindResidentMeterDTO(
        @NotNull(message = "居民ID不能为空")
        @Positive(message = "居民ID必须大于0")
        Long residentId,

        @NotNull(message = "表具ID不能为空")
        @Positive(message = "表具ID必须大于0")
        Long meterId
) {
}
