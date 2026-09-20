package com.byy.meterreading.dto.meterreadingtask;

import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 抄表员查询本人任务时使用的分页和状态筛选参数。
 */
public record MyMeterReadingTaskPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize,

        MeterReadingTaskStatus taskStatus
) {
    public MyMeterReadingTaskPageQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
