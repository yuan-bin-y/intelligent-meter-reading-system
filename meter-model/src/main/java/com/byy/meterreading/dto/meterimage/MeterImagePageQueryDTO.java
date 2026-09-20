package com.byy.meterreading.dto.meterimage;

import com.byy.meterreading.model.enums.MeterImageStatus;
import com.byy.meterreading.model.enums.MeterImageStorageStatus;
import com.byy.meterreading.model.enums.TaskExecutorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 管理员分页查询抄表图片的筛选条件。 */
public record MeterImagePageQueryDTO(
        @Min(value = 1, message = "页码不能小于1") Integer page,
        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100") Integer pageSize,
        @Size(max = 64, message = "任务编号长度不能超过64个字符") String taskNo,
        @Size(max = 64, message = "表号长度不能超过64个字符") String meterNo,
        TaskExecutorType uploaderType,
        @Positive(message = "上传者ID必须大于0") Long uploaderId,
        MeterImageStatus imageStatus,
        MeterImageStorageStatus storageStatus,
        Boolean bound,
        Boolean deleted,
        LocalDateTime createdAtStart,
        LocalDateTime createdAtEnd
) {
    public MeterImagePageQueryDTO {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        taskNo = trimToNull(taskNo);
        meterNo = trimToNull(meterNo);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
