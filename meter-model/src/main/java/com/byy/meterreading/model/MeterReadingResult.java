package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抄表员或设备提交的待审核结果，对应 meter_reading_result 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("meter_reading_result")
public class MeterReadingResult {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 每个任务只能产生一条待审核结果。 */
    private Long taskId;
    private Long meterId;

    /** METER_READER 或 DEVICE。 */
    private String sourceType;
    private Long meterReaderId;
    private Long deviceId;

    private BigDecimal readingValue;
    private String imageUrl;

    /** 设备识别结果必填，人工提交时为空。 */
    private BigDecimal recognitionConfidence;
    private String remark;

    /** 审核模块后续根据该字段处理结果。 */
    private String reviewStatus;
    private LocalDateTime submittedAt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
