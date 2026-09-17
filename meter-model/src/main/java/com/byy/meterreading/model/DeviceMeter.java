package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备与表具绑定关系实体，对应 device_meter 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("device_meter")
public class DeviceMeter {

    private Long deviceId;
    private Long meterId;
    private Long createdBy;
    private LocalDateTime createdAt;
}
