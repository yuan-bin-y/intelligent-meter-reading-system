package com.byy.meterreading.model.enums;

/**
 * 表具测量类型。
 */
public enum MeterType {

    WATER("水表"),
    ELECTRIC("电表"),
    GAS("燃气表");

    private final String description;

    MeterType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
