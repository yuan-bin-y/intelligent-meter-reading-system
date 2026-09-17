package com.byy.meterreading.dto.meter;

import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 管理员修改表具基础资料请求；表具编号和状态使用独立流程维护。
 */
public record UpdateMeterDTO(

        @NotBlank(message = "表具名称不能为空")
        @Size(max = 64, message = "表具名称长度不能超过64个字符")
        String meterName,

        @NotNull(message = "表具类型不能为空")
        MeterType meterType,

        @NotNull(message = "显示类型不能为空")
        MeterDisplayType displayType,

        @NotBlank(message = "计量单位不能为空")
        @Size(max = 16, message = "计量单位长度不能超过16个字符")
        String unit,

        @NotNull(message = "整数位数不能为空")
        @Min(value = 1, message = "整数位数不能小于1")
        @Max(value = 12, message = "整数位数不能大于12")
        Integer integerDigits,

        @NotNull(message = "小数位数不能为空")
        @Min(value = 0, message = "小数位数不能小于0")
        @Max(value = 3, message = "小数位数不能大于3")
        Integer decimalDigits,

        @NotNull(message = "初始读数不能为空")
        @DecimalMin(value = "0.000", message = "初始读数不能小于0")
        @Digits(
                integer = 15,
                fraction = 3,
                message = "初始读数最多15位整数和3位小数"
        )
        BigDecimal initialReading,

        @PastOrPresent(message = "安装日期不能晚于今天")
        LocalDate installedAt,

        @Size(max = 500, message = "备注长度不能超过500个字符")
        String remark,

        @NotNull(message = "数据版本不能为空")
        @Min(value = 0, message = "数据版本不能小于0")
        Integer version
) {
}
