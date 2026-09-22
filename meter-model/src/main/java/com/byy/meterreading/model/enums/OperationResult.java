package com.byy.meterreading.model.enums;

/** 操作审计结果。 */
public enum OperationResult {

    SUCCESS("操作成功"),
    FAILED("操作失败");

    private final String description;

    OperationResult(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
