package com.byy.meterreading.model.enums;

/**
 * 抄表任务执行者类型。
 */
public enum TaskExecutorType {

    METER_READER("抄表员"),
    DEVICE("采集设备");

    private final String description;

    TaskExecutorType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
