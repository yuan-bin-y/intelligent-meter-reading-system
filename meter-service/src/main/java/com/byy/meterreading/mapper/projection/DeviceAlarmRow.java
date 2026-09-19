package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备告警表与设备表关联查询的数据库投影。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAlarmRow {

    private Long id;
    private Long deviceId;
    private String deviceNo;
    private String deviceName;
    private String alarmType;
    private String alarmStatus;
    private LocalDateTime occurredAt;
    private LocalDateTime recoveredAt;
}
