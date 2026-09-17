package com.byy.meterreading.model.enums;

/**
 * 表具读数显示类型。
 */
public enum MeterDisplayType {

    LCD("液晶显示型"),
    MECHANICAL_ROLLER("机械滚轮型");

    private final String description;

    MeterDisplayType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
