package com.byy.meterreading.dto.meterreadingtask;

import com.byy.meterreading.model.enums.TaskExecutorType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 管理员创建抄表任务的请求参数。
 */
public record CreateMeterReadingTaskDTO(
        @NotNull(message = "表具ID不能为空")
        @Positive(message = "表具ID必须大于0")
        Long meterId,

        @NotNull(message = "执行者类型不能为空")
        TaskExecutorType executorType,

        @NotNull(message = "执行者ID不能为空")
        @Positive(message = "执行者ID必须大于0")
        Long executorId,

        @NotNull(message = "计划执行时间不能为空")
        LocalDateTime scheduledAt,

        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {

    public CreateMeterReadingTaskDTO {
        remark = trimToNull(remark);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
