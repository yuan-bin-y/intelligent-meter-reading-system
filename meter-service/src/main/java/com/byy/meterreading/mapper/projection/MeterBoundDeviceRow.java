package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 查询表具已绑定设备时使用的数据库投影。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeterBoundDeviceRow {

    private Long deviceId;
    private String deviceNo;
    private String deviceName;
    private String deviceType;
    private Integer status;
    private Integer version;
    private LocalDateTime boundAt;
}
