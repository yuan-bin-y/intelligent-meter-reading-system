package com.byy.meterreading.dto.meterreadingrecord;

import com.byy.meterreading.model.enums.TaskExecutorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 管理员或居民分页查询正式抄表记录的条件。 */
public record MeterReadingRecordPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1") Integer page,
        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100") Integer pageSize,
        @Size(max = 64, message = "关键字长度不能超过64个字符") String keyword,
        @Positive(message = "表具ID必须大于0") Long meterId,
        TaskExecutorType sourceType,
        LocalDateTime readingAtStart,
        LocalDateTime readingAtEnd
) {
    public MeterReadingRecordPageQueryDTO {
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
