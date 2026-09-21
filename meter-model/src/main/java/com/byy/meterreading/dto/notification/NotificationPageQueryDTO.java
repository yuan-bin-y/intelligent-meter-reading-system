package com.byy.meterreading.dto.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 当前用户的通知分页查询参数。 */
public record NotificationPageQueryDTO(
        Boolean unreadOnly,

        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer pageSize
) {

    public NotificationPageQueryDTO {
        unreadOnly = Boolean.TRUE.equals(unreadOnly);
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
