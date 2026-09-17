package com.byy.meterreading.dto.residentmeter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 管理员分页查询居民表具绑定关系的请求参数。
 */
public record ResidentMeterPageQueryDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize,

        @Size(max = 64, message = "查询关键字长度不能超过64个字符")
        String keyword,

        @Positive(message = "居民ID必须大于0")
        Long residentId,

        @Positive(message = "表具ID必须大于0")
        Long meterId
) {

    public ResidentMeterPageQueryDTO {
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
