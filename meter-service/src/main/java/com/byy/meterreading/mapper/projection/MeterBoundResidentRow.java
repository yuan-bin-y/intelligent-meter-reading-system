package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 查询表具绑定居民时使用的数据库投影。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeterBoundResidentRow {

    private Long residentId;
    private String username;
    private String displayName;
    private Integer status;
    private LocalDateTime boundAt;
}
