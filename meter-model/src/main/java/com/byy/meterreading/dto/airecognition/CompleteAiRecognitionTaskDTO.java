package com.byy.meterreading.dto.airecognition;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** AI 服务识别成功后的结果回调参数。 */
public record CompleteAiRecognitionTaskDTO(
        @NotBlank(message = "事件编号不能为空")
        @Size(max = 64, message = "事件编号长度不能超过64个字符") String eventId,
        @Positive(message = "识别尝试序号必须大于0") int attemptNo,
        @NotNull(message = "识别读数不能为空")
        @DecimalMin(value = "0", message = "识别读数不能小于0")
        @Digits(integer = 15, fraction = 3, message = "识别读数最多15位整数和3位小数")
        BigDecimal recognizedValue,
        @NotNull(message = "识别置信度不能为空")
        @DecimalMin(value = "0", message = "识别置信度不能小于0")
        @DecimalMax(value = "1", message = "识别置信度不能大于1")
        @Digits(integer = 1, fraction = 4, message = "识别置信度最多保留4位小数")
        BigDecimal confidence,
        @NotBlank(message = "模型名称不能为空")
        @Size(max = 128, message = "模型名称长度不能超过128个字符") String modelName,
        @NotBlank(message = "模型版本不能为空")
        @Size(max = 64, message = "模型版本长度不能超过64个字符") String modelVersion,
        @NotBlank(message = "原始识别结果不能为空") String rawResult,
        @NotNull(message = "识别耗时不能为空")
        @PositiveOrZero(message = "识别耗时不能小于0") Long processingDurationMs
) {
    public CompleteAiRecognitionTaskDTO {
        eventId = trim(eventId);
        modelName = trim(modelName);
        modelVersion = trim(modelVersion);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
