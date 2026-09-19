package com.byy.meterreading.model.enums;

/**
 * 设备告警类型。
 */
public enum DeviceAlarmType {

    OFFLINE("设备离线");

    private final String description;

    DeviceAlarmType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
