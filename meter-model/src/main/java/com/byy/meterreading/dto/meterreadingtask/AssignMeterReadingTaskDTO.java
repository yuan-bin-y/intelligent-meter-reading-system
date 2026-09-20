package com.byy.meterreading.dto.meterreadingtask;

import com.byy.meterreading.model.enums.TaskExecutorType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 管理员重新分配抄表任务的请求参数。
 */
public record AssignMeterReadingTaskDTO(
        @NotNull(message = "执行者类型不能为空")
        TaskExecutorType executorType,

        @NotNull(message = "执行者ID不能为空")
        @Positive(message = "执行者ID必须大于0")
        Long executorId,

        @NotNull(message = "数据版本不能为空")
        @PositiveOrZero(message = "数据版本不能小于0")
        Integer version
) {
}
