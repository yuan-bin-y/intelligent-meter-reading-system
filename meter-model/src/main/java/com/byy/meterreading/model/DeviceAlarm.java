package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备告警实体，对应 device_alarm 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("device_alarm")
public class DeviceAlarm {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    /** 当前支持 OFFLINE。 */
    private String alarmType;

    /** OPEN-未恢复，RECOVERED-已恢复。 */
    private String alarmStatus;

    private LocalDateTime occurredAt;
    private LocalDateTime recoveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
