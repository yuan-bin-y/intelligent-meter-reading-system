package com.byy.meterreading.vo.meterimage;

import com.byy.meterreading.model.enums.MeterImageStatus;

/** 图片失效或恢复后的最新业务状态和版本。 */
public record MeterImageVersionVO(
        Long imageId,
        MeterImageStatus imageStatus,
        Integer version
) {
}
