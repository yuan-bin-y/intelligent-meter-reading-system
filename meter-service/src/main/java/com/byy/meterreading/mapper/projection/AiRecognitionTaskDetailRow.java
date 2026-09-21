package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 识别任务、业务关联和最新 Outbox 事件的详情查询投影。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecognitionTaskDetailRow {
    private Long recognitionTaskId;
    private String recognitionNo;
    private Long readingTaskId;
    private String readingTaskNo;
    private String readingTaskStatus;
    private Long imageId;
    private String imageOriginalName;
    private String imageType;
    private String imageStatus;
    private String imageStorageStatus;
    private Long meterId;
    private String meterNo;
    private String meterName;
    private String meterType;
    private String unit;
    private Integer attemptNo;
    private String status;
    private Long resultId;
    private BigDecimal recognizedValue;
    private BigDecimal confidence;
    private String modelName;
    private String modelVersion;
    private String rawResult;
    private String failureCode;
    private String failureMessage;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long processingDurationMs;
    private String cancelReason;
    private Long cancelledBy;
    private String cancelledByName;
    private LocalDateTime cancelledAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String latestEventId;
    private String latestOutboxStatus;
    private Integer latestOutboxRetryCount;
    private Integer latestOutboxMaxRetryCount;
    private LocalDateTime latestOutboxNextRetryAt;
    private LocalDateTime latestOutboxSentAt;
    private String latestOutboxLastError;
}
