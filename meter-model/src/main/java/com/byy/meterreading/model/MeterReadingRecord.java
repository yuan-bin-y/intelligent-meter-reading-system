package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 审核通过后生成的不可修改正式抄表记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("meter_reading_record")
public class MeterReadingRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long resultId;
    private Long taskId;
    private Long meterId;
    private BigDecimal readingValue;
    private LocalDateTime readingAt;
    private String sourceType;
    private Long executorId;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
