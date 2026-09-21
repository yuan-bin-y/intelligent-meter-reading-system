package com.byy.meterreading.vo.airecognition;

import com.byy.meterreading.model.enums.AiRecognitionStatus;
import com.byy.meterreading.model.enums.MeterImageStatus;
import com.byy.meterreading.model.enums.MeterImageStorageStatus;
import com.byy.meterreading.model.enums.MeterImageType;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.MqOutboxStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 识别任务、业务关联和最新 Outbox 消息的组合详情。 */
public record AiRecognitionTaskDetailVO(
        Long recognitionTaskId,
        String recognitionNo,
        Long readingTaskId,
        String readingTaskNo,
        MeterReadingTaskStatus readingTaskStatus,
        String readingTaskStatusName,
        Long imageId,
        String imageOriginalName,
        MeterImageType imageType,
        String imageTypeName,
        MeterImageStatus imageStatus,
        String imageStatusName,
        MeterImageStorageStatus imageStorageStatus,
        String imageStorageStatusName,
        Long meterId,
        String meterNo,
        String meterName,
        String meterType,
        String unit,
        Integer attemptNo,
        AiRecognitionStatus status,
        String statusName,
        Long resultId,
        BigDecimal recognizedValue,
        BigDecimal confidence,
        String modelName,
        String modelVersion,
        String rawResult,
        String failureCode,
        String failureMessage,
        Integer retryCount,
        Integer maxRetryCount,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long processingDurationMs,
        String cancelReason,
        Long cancelledBy,
        String cancelledByName,
        LocalDateTime cancelledAt,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String latestEventId,
        MqOutboxStatus latestOutboxStatus,
        String latestOutboxStatusName,
        Integer latestOutboxRetryCount,
        Integer latestOutboxMaxRetryCount,
        LocalDateTime latestOutboxNextRetryAt,
        LocalDateTime latestOutboxSentAt,
        String latestOutboxLastError
) {
}
