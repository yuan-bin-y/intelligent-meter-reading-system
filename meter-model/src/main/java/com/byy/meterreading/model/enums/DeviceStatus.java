package com.byy.meterreading.model.enums;

/**
 * 设备管理状态；在线状态由 Redis 心跳单独判断。
 */
public enum DeviceStatus {

    DISABLED(0, "停用"),
    ENABLED(1, "启用");

    private final int code;
    private final String description;

    DeviceStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
