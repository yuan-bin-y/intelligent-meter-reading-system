package com.byy.meterreading.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 抄表结果关联任务、表具和执行者后的查询投影。 */
@Data
public class MeterReadingResultRow {
    private Long resultId;
    private Long taskId;
    private String taskNo;
    private Integer attemptNo;
    private Long meterId;
    private String meterNo;
    private String meterName;
    private String meterType;
    private String unit;
    private BigDecimal readingValue;
    private BigDecimal confirmedReadingValue;
    private String sourceType;
    private Long executorId;
    private String executorCode;
    private String executorName;
    private BigDecimal recognitionConfidence;
    private String remark;
    private String reviewStatus;
    private String taskStatus;
    private Integer resultVersion;
    private Integer taskVersion;
    private LocalDateTime submittedAt;
}
