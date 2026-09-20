package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 一次抄表结果的不可覆盖审核记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("meter_reading_review")
public class MeterReadingReview {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long resultId;
    private Long taskId;
    private String reviewAction;
    private Long reviewerId;
    private String reviewReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
