package com.byy.meterreading.model.enums;

/**
 * 设备告警处理状态。
 */
public enum DeviceAlarmStatus {

    OPEN("未恢复"),
    RECOVERED("已恢复");

    private final String description;

    DeviceAlarmStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
