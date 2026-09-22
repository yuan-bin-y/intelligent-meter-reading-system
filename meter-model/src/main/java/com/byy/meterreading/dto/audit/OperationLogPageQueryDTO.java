package com.byy.meterreading.dto.audit;

import com.byy.meterreading.model.enums.OperationResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 管理员分页查询操作审计日志的筛选条件。 */
public record OperationLogPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize,

        @Size(max = 64, message = "traceId长度不能超过64个字符")
        String traceId,

        @Size(max = 64, message = "操作人用户名长度不能超过64个字符")
        String operatorUsername,

        @Size(max = 64, message = "业务模块长度不能超过64个字符")
        String module,

        OperationResult result,

        LocalDateTime createdAtStart,
        LocalDateTime createdAtEnd
) {

    public OperationLogPageQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        traceId = trimToNull(traceId);
        operatorUsername = trimToNull(operatorUsername);
        module = trimToNull(module);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
