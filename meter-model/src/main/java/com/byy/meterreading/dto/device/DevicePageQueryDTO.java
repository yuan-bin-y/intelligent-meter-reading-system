package com.byy.meterreading.dto.device;

import com.byy.meterreading.model.enums.DeviceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 管理员分页查询设备请求参数。
 */
public record DevicePageQueryDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize,

        @Size(max = 64, message = "查询关键字长度不能超过64个字符")
        String keyword,

        DeviceType deviceType,

        @Min(value = 0, message = "设备状态只能是0或1")
        @Max(value = 1, message = "设备状态只能是0或1")
        Integer status
) {

    public DevicePageQueryDTO {
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
