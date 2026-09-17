package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 完整居民表具绑定关系的数据库查询投影。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResidentMeterBindingRow {

    private Long residentId;
    private String username;
    private String displayName;
    private Integer residentStatus;
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
