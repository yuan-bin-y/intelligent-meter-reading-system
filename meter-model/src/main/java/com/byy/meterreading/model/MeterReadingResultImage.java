package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 抄表结果与多张图片的关联实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("meter_reading_result_image")
public class MeterReadingResultImage {
    private Long resultId;
    private Long imageId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
