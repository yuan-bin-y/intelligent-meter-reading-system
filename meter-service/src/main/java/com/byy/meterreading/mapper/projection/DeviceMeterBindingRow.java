package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 完整设备表具绑定关系的数据库查询投影。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMeterBindingRow {

    private Long deviceId;
    private String deviceNo;
    private String deviceName;
    private String deviceType;
    private Integer deviceStatus;
    private Long meterId;
    private String meterNo;
    private String meterName;
    private String meterType;
    private String displayType;
    private String unit;
    private Integer meterStatus;
    private Long createdBy;
    private LocalDateTime boundAt;
}
