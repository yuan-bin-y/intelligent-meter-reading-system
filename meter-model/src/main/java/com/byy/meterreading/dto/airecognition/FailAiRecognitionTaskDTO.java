package com.byy.meterreading.dto.airecognition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** AI 服务识别失败后的结果回调参数。 */
public record FailAiRecognitionTaskDTO(
        @NotBlank(message = "事件编号不能为空")
        @Size(max = 64, message = "事件编号长度不能超过64个字符") String eventId,
        @Positive(message = "识别尝试序号必须大于0") int attemptNo,
        @NotBlank(message = "失败编码不能为空")
        @Size(max = 64, message = "失败编码长度不能超过64个字符") String failureCode,
        @NotBlank(message = "失败原因不能为空")
        @Size(max = 500, message = "失败原因长度不能超过500个字符") String failureMessage,
        @NotNull(message = "识别耗时不能为空")
        @PositiveOrZero(message = "识别耗时不能小于0") Long processingDurationMs
) {
    public FailAiRecognitionTaskDTO {
        eventId = trim(eventId);
        failureCode = trim(failureCode);
        failureMessage = trim(failureMessage);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
