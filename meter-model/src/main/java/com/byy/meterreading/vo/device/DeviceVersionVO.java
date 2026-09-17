package com.byy.meterreading.vo.device;

/**
 * 设备修改成功后返回的最新乐观锁版本。
 */
public record DeviceVersionVO(
        Long deviceId,
        Integer version
) {
}
