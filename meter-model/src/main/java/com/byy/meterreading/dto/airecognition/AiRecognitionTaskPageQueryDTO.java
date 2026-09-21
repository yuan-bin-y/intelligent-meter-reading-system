package com.byy.meterreading.dto.airecognition;

import com.byy.meterreading.model.enums.AiRecognitionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 管理员分页查询 AI 识别任务的筛选条件。 */
public record AiRecognitionTaskPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1") Integer page,
        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100") Integer pageSize,
        @Size(max = 64, message = "识别任务编号长度不能超过64个字符") String recognitionNo,
        @Size(max = 64, message = "抄表任务编号长度不能超过64个字符") String readingTaskNo,
        @Size(max = 64, message = "表号长度不能超过64个字符") String meterNo,
        AiRecognitionStatus status,
        @Size(max = 128, message = "模型名称长度不能超过128个字符") String modelName,
        LocalDateTime createdAtStart,
        LocalDateTime createdAtEnd
) {
    public AiRecognitionTaskPageQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        recognitionNo = trimToNull(recognitionNo);
        readingTaskNo = trimToNull(readingTaskNo);
        meterNo = trimToNull(meterNo);
        modelName = trimToNull(modelName);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
