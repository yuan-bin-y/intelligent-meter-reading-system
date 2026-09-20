package com.byy.meterreading.dto.meterreadingreview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 审核驳回请求；驳回原因会同时写入审核历史和任务失败原因。 */
public record RejectMeterReadingResultDTO(
        @NotNull(message = "结果版本不能为空")
        @PositiveOrZero(message = "结果版本不能小于0") Integer resultVersion,
        @NotNull(message = "任务版本不能为空")
        @PositiveOrZero(message = "任务版本不能小于0") Integer taskVersion,
        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 450, message = "驳回原因长度不能超过450个字符") String reason
) {
    public RejectMeterReadingResultDTO {
        reason = reason == null ? null : reason.trim();
    }
}
