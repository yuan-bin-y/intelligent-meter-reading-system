package com.byy.meterreading.dto.meterreadingtask;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 管理员取消抄表任务的请求参数。
 */
public record CancelMeterReadingTaskDTO(
        @NotNull(message = "数据版本不能为空")
        @PositiveOrZero(message = "数据版本不能小于0")
        Integer version,

        @NotBlank(message = "取消原因不能为空")
        @Size(max = 500, message = "取消原因长度不能超过500个字符")
        String reason
) {

    public CancelMeterReadingTaskDTO {
        reason = reason == null ? null : reason.trim();
    }
}
