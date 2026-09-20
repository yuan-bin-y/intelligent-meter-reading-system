package com.byy.meterreading.vo.meterreadingtask;

import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.MeterType;
import com.byy.meterreading.model.enums.TaskExecutorType;

import java.time.LocalDateTime;

/**
 * 抄表任务完整详情；创建任务和查询详情共用该响应结构。
 */
public record MeterReadingTaskDetailVO(
        Long taskId,
        String taskNo,
        Long meterId,
        String meterNo,
        String meterName,
        MeterType meterType,
        MeterDisplayType displayType,
        String unit,
        TaskExecutorType executorType,
        String executorTypeName,
        Long executorId,
        String executorCode,
        String executorName,
        MeterReadingTaskStatus taskStatus,
        String taskStatusName,
        LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        String failedReason,
        String cancelReason,
        Integer retryCount,
        Integer version,
        String remark,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
