package com.byy.meterreading.vo.residentmeter;

import java.time.LocalDateTime;

/**
 * 指定表具绑定的一条居民信息。
 */
public record MeterBoundResidentVO(
        Long residentId,
        String username,
        String displayName,
        Integer status,
        LocalDateTime boundAt
) {
}
