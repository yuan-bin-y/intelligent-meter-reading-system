package com.byy.meterreading.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 正式抄表记录关联业务信息后的查询投影。 */
@Data
public class MeterReadingRecordRow {
    private Long recordId;
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
    private LocalDateTime readingAt;
    private String sourceType;
    private Long executorId;
    private String executorCode;
    private String executorName;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewedAt;
}
