package com.byy.meterreading.dto.meterreadingreview;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 审核通过请求，同时携带结果和任务的乐观锁版本。 */
public record ApproveMeterReadingResultDTO(
        @NotNull(message = "结果版本不能为空")
        @PositiveOrZero(message = "结果版本不能小于0") Integer resultVersion,
        @NotNull(message = "任务版本不能为空")
        @PositiveOrZero(message = "任务版本不能小于0") Integer taskVersion,
        @Size(max = 500, message = "审核说明长度不能超过500个字符") String remark
) {
    public ApproveMeterReadingResultDTO {
        remark = trimToNull(remark);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
