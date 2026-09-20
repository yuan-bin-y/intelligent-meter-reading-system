package com.byy.meterreading.dto.meterreadingtask;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 只需要校验乐观锁版本的抄表任务操作参数。
 */
public record MeterReadingTaskVersionDTO(
        @NotNull(message = "数据版本不能为空")
        @PositiveOrZero(message = "数据版本不能小于0")
        Integer version
) {
}
