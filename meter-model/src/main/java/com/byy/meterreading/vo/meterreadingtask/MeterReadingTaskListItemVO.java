package com.byy.meterreading.vo.meterreadingtask;

import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.TaskExecutorType;

import java.time.LocalDateTime;

/**
 * 管理员分页查询抄表任务时使用的精简列表项。
 */
public record MeterReadingTaskListItemVO(
        Long taskId,
        String taskNo,
        Long meterId,
        String meterNo,
        String meterName,
        TaskExecutorType executorType,
        String executorTypeName,
        Long executorId,
        String executorName,
        MeterReadingTaskStatus taskStatus,
        String taskStatusName,
        LocalDateTime scheduledAt,
        Integer retryCount,
        Integer version,
        LocalDateTime updatedAt
) {
}
