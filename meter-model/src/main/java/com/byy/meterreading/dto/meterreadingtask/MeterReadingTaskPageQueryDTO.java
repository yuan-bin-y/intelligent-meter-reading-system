package com.byy.meterreading.dto.meterreadingtask;

import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.TaskExecutorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 管理员分页查询抄表任务的请求参数。
 */
public record MeterReadingTaskPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize,

        @Size(max = 64, message = "查询关键字长度不能超过64个字符")
        String keyword,

        TaskExecutorType executorType,

        MeterReadingTaskStatus taskStatus,

        @Positive(message = "抄表员ID必须大于0")
        Long meterReaderId,

        @Positive(message = "设备ID必须大于0")
        Long deviceId,

        LocalDateTime scheduledAtStart,

        LocalDateTime scheduledAtEnd
) {

    public MeterReadingTaskPageQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        keyword = trimToNull(keyword);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
