package com.byy.meterreading.dto.airecognition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** AI 服务收到 MQ 消息并开始处理时提交的信息。 */
public record StartAiRecognitionTaskDTO(
        @NotBlank(message = "事件编号不能为空")
        @Size(max = 64, message = "事件编号长度不能超过64个字符") String eventId,
        @Positive(message = "识别尝试序号必须大于0") int attemptNo,
        @NotBlank(message = "模型名称不能为空")
        @Size(max = 128, message = "模型名称长度不能超过128个字符") String modelName,
        @NotBlank(message = "模型版本不能为空")
        @Size(max = 64, message = "模型版本长度不能超过64个字符") String modelVersion
) {
    public StartAiRecognitionTaskDTO {
        eventId = trim(eventId);
        modelName = trim(modelName);
        modelVersion = trim(modelVersion);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
