package com.byy.meterreading.vo.audit;

import com.byy.meterreading.model.enums.OperationResult;

import java.time.LocalDateTime;

/** 管理员操作日志分页列表中的单条摘要。 */
public record OperationLogListVO(
        Long logId,
        String traceId,
        Long operatorId,
        String operatorUsername,
        String module,
        String action,
        String resourceType,
        Long resourceId,
        OperationResult result,
        String resultName,
        Long durationMs,
        LocalDateTime createdAt
) {
}
