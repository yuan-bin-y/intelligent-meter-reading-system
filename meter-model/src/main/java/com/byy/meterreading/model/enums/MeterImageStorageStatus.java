package com.byy.meterreading.model.enums;

/** OSS 对象生命周期状态。 */
public enum MeterImageStorageStatus {
    STORED("已存储"),
    DELETE_PENDING("等待删除"),
    DELETED("已删除");

    private final String description;

    MeterImageStorageStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
