package com.byy.meterreading.dto.meter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员修改表具状态请求。
 */
public record UpdateMeterStatusDTO(

        @NotNull(message = "表具状态不能为空")
        @Min(value = 0, message = "表具状态只能是0到3")
        @Max(value = 3, message = "表具状态只能是0到3")
        Integer status,

        @NotNull(message = "数据版本不能为空")
        @Min(value = 0, message = "数据版本不能小于0")
        Integer version
) {
}
