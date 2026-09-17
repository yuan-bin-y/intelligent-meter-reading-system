package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 查询设备已绑定表具时使用的数据库投影。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceBoundMeterRow {

    private Long meterId;
    private String meterNo;
    private String meterName;
    private String meterType;
    private String displayType;
    private String unit;
    private Integer status;
    private Integer version;
    private LocalDateTime boundAt;
}
