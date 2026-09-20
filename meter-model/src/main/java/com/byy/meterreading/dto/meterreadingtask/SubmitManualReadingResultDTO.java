package com.byy.meterreading.dto.meterreadingtask;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 抄表员提交人工读数和现场照片的请求参数。
 */
public record SubmitManualReadingResultDTO(
        @NotNull(message = "数据版本不能为空")
        @PositiveOrZero(message = "数据版本不能小于0")
        Integer version,

        @NotNull(message = "表具读数不能为空")
        @DecimalMin(value = "0", message = "表具读数不能小于0")
        @Digits(integer = 12, fraction = 3,
                message = "表具读数最多12位整数和3位小数")
        BigDecimal readingValue,

        @NotBlank(message = "抄表照片地址不能为空")
        @Size(max = 1024, message = "抄表照片地址长度不能超过1024个字符")
        String imageUrl,

        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark
) {
    public SubmitManualReadingResultDTO {
        imageUrl = imageUrl == null ? null : imageUrl.trim();
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
