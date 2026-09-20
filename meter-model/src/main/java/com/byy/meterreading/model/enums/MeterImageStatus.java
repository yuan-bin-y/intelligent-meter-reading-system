package com.byy.meterreading.model.enums;

/** 图片业务有效性状态。 */
public enum MeterImageStatus {
    VALID("有效"),
    INVALID("无效");

    private final String description;

    MeterImageStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
