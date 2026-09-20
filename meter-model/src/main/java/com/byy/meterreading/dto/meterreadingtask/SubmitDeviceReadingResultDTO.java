package com.byy.meterreading.dto.meterreadingtask;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 设备提交识别读数、采集图片和模型置信度的请求参数。
 */
public record SubmitDeviceReadingResultDTO(
        @NotNull(message = "数据版本不能为空")
        @PositiveOrZero(message = "数据版本不能小于0")
        Integer version,

        @NotNull(message = "识别读数不能为空")
        @DecimalMin(value = "0", message = "识别读数不能小于0")
        @Digits(integer = 12, fraction = 3,
                message = "识别读数最多12位整数和3位小数")
        BigDecimal recognizedReading,

        @NotEmpty(message = "至少选择一张采集图片")
        @Size(max = 5, message = "一次最多提交5张采集图片")
        List<@NotNull(message = "图片ID不能为空")
                @Positive(message = "图片ID必须大于0") Long> imageIds,

        @NotNull(message = "识别置信度不能为空")
        @DecimalMin(value = "0", message = "识别置信度不能小于0")
        @DecimalMax(value = "1", message = "识别置信度不能大于1")
        @Digits(integer = 1, fraction = 4,
                message = "识别置信度最多保留4位小数")
        BigDecimal confidence,

        @Size(max = 500, message = "附加信息长度不能超过500个字符")
        String remark
) {
    public SubmitDeviceReadingResultDTO {
        imageIds = imageIds == null ? null : List.copyOf(imageIds);
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
