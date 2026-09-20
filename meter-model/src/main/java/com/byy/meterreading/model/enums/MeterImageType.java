package com.byy.meterreading.model.enums;

/** 抄表图片用途。 */
public enum MeterImageType {
    ORIGINAL("原始表具图片"),
    AI_ANNOTATED("AI标注图片"),
    ENVIRONMENT("现场环境图片");

    private final String description;

    MeterImageType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
