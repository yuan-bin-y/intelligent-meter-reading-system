package com.byy.meterreading.vo.meterreadingtask;

import com.byy.meterreading.model.enums.MeterReadingTaskStatus;

/**
 * 任务重新分配、取消和重试成功后的最新状态及乐观锁版本。
 */
public record MeterReadingTaskVersionVO(
        Long taskId,
        MeterReadingTaskStatus taskStatus,
        Integer version
) {
}
