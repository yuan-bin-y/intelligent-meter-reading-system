package com.byy.meterreading.model.enums;

import java.util.Arrays;

/**
 * 表具生命周期状态。
 */
public enum MeterStatus {

    DISABLED(0, "停用"),
    ACTIVE(1, "正常"),
    MAINTENANCE(2, "维护中"),
    SCRAPPED(3, "已报废");

    private final int code;
    private final String description;

    MeterStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断当前状态是否允许流转到目标状态。
     */
    public boolean canTransitionTo(MeterStatus target) {
        if (target == null || target == this || this == SCRAPPED) {
            return false;
        }
        return switch (this) {
            case ACTIVE -> target == DISABLED
                    || target == MAINTENANCE
                    || target == SCRAPPED;
            case DISABLED -> target == ACTIVE || target == SCRAPPED;
            case MAINTENANCE -> target == ACTIVE || target == SCRAPPED;
            case SCRAPPED -> false;
        };
    }

    /**
     * 根据数据库状态码查找枚举；非法状态码由调用方统一按参数错误处理。
     */
    public static MeterStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知表具状态：" + code
                ));
    }
}
