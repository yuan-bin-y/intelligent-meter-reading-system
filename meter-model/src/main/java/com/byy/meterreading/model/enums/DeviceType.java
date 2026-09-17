package com.byy.meterreading.model.enums;

/**
 * 采集设备类型。
 */
public enum DeviceType {

    CAMERA("摄像头"),
    GATEWAY("网关"),
    EDGE_DEVICE("边缘设备");

    private final String description;

    DeviceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
