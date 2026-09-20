package com.byy.meterreading.dto.meterimage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 管理员标记图片无效或恢复图片时提交的参数。 */
public record UpdateMeterImageStatusDTO(
        @NotNull(message = "数据版本不能为空")
        @PositiveOrZero(message = "数据版本不能小于0") Integer version,
        @NotBlank(message = "操作原因不能为空")
        @Size(max = 500, message = "操作原因长度不能超过500个字符") String reason
) {
    public UpdateMeterImageStatusDTO {
        reason = reason == null ? null : reason.trim();
    }
}
