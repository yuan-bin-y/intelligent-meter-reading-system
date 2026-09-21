package com.byy.meterreading.dto.meterreadingreview;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 审核通过请求，同时携带结果和任务的乐观锁版本。 */
public record ApproveMeterReadingResultDTO(
        @NotNull(message = "结果版本不能为空")
        @PositiveOrZero(message = "结果版本不能小于0") Integer resultVersion,
        @NotNull(message = "任务版本不能为空")
        @PositiveOrZero(message = "任务版本不能小于0") Integer taskVersion,
        @NotNull(message = "最终确认读数不能为空")
        @DecimalMin(value = "0", message = "最终确认读数不能小于0")
        @Digits(integer = 15, fraction = 3,
                message = "最终确认读数最多15位整数和3位小数")
        java.math.BigDecimal confirmedReadingValue,
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
