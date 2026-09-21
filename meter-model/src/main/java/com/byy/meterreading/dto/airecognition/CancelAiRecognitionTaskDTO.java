package com.byy.meterreading.dto.airecognition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 管理员取消尚未完成的 AI 识别任务。 */
public record CancelAiRecognitionTaskDTO(
        @NotNull(message = "识别任务版本不能为空")
        @PositiveOrZero(message = "识别任务版本不能小于0") Integer version,
        @NotBlank(message = "取消原因不能为空")
        @Size(max = 500, message = "取消原因长度不能超过500个字符") String reason
) {
    public CancelAiRecognitionTaskDTO {
        reason = reason == null ? null : reason.trim();
    }
}
