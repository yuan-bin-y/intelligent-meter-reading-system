package com.byy.meterreading.dto.meterreadingreview;

import com.byy.meterreading.model.enums.MeterReadingReviewStatus;
import com.byy.meterreading.model.enums.TaskExecutorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 审核端分页查询抄表结果的筛选条件。 */
public record MeterReadingResultPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1") Integer page,
        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100") Integer pageSize,
        @Size(max = 64, message = "关键字长度不能超过64个字符") String keyword,
        TaskExecutorType sourceType,
        MeterReadingReviewStatus reviewStatus,
        LocalDateTime submittedAtStart,
        LocalDateTime submittedAtEnd
) {
    public MeterReadingResultPageQueryDTO {
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
