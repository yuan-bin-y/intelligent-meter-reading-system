package com.byy.meterreading.dto.meterreadingtask;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 设备拉取分配给自己的待执行任务时使用的分页参数。
 */
public record DevicePendingTaskPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize
) {
    public DevicePendingTaskPageQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
