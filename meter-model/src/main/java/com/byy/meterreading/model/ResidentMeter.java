package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 居民与表具绑定关系实体，对应 resident_meter 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("resident_meter")
public class ResidentMeter {

    private Long residentId;
    private Long meterId;
    private Long createdBy;
    private LocalDateTime createdAt;
}
