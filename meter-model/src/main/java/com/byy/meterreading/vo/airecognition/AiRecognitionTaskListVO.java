package com.byy.meterreading.vo.airecognition;

import com.byy.meterreading.model.enums.AiRecognitionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理员分页查询 AI 识别任务时使用的列表项。 */
public record AiRecognitionTaskListVO(
        Long recognitionTaskId,
        String recognitionNo,
        Long readingTaskId,
        String readingTaskNo,
        Long imageId,
        Long meterId,
        String meterNo,
        String meterName,
        Integer attemptNo,
        AiRecognitionStatus status,
        String statusName,
        BigDecimal recognizedValue,
        BigDecimal confidence,
        String modelName,
        String modelVersion,
        Integer retryCount,
        Integer maxRetryCount,
        Integer version,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
}
