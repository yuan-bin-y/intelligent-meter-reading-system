package com.byy.meterreading.vo.device;

/**
 * 新增设备成功后的响应数据。
 */
public record CreateDeviceVO(
        Long deviceId,
        String deviceNo
) {
}
