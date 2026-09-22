package com.byy.meterreading.vo.audit;

import com.byy.meterreading.model.enums.OperationResult;

import java.time.LocalDateTime;

/** 管理员查看的单条操作审计日志完整详情。 */
public record OperationLogDetailVO(
        Long logId,
        String traceId,
        Long operatorId,
        String operatorUsername,
        String module,
        String action,
        String resourceType,
        Long resourceId,
        String requestMethod,
        String requestPath,
        String clientIp,
        String requestParams,
        OperationResult result,
        String resultName,
        String errorMessage,
        Long durationMs,
        LocalDateTime createdAt
) {
}
