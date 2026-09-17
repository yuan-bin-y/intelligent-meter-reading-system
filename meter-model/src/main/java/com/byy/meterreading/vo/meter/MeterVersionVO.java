package com.byy.meterreading.vo.meter;

/**
 * 表具修改成功后返回的最新乐观锁版本。
 */
public record MeterVersionVO(
        Long meterId,
        Integer version
) {
}
