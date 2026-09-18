package com.byy.meterreading.vo.device;

import java.time.LocalDateTime;

/**
 * 管理员重置设备密钥后的响应数据。
 * deviceSecret 只在本次响应中返回，不能再次查询。
 */
public record ResetDeviceSecretVO(
        Long deviceId,
        String deviceNo,
        String deviceSecret,
        Integer credentialVersion,
        Integer version,
        LocalDateTime rotatedAt
) {
}
