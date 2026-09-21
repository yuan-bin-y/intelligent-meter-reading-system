package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 识别任务管理员分页查询数据库投影。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecognitionTaskListRow {
    private Long recognitionTaskId;
    private String recognitionNo;
    private Long readingTaskId;
    private String readingTaskNo;
    private Long imageId;
    private Long meterId;
    private String meterNo;
    private String meterName;
    private Integer attemptNo;
    private String status;
    private BigDecimal recognizedValue;
    private BigDecimal confidence;
    private String modelName;
    private String modelVersion;
    private Integer retryCount;
    private Integer maxRetryCount;
    private Integer version;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
