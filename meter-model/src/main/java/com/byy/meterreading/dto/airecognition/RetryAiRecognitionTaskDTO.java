package com.byy.meterreading.dto.airecognition;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 管理员人工重试失败识别任务时携带的并发版本。 */
public record RetryAiRecognitionTaskDTO(
        @NotNull(message = "识别任务版本不能为空")
        @PositiveOrZero(message = "识别任务版本不能小于0") Integer version
) {
}
