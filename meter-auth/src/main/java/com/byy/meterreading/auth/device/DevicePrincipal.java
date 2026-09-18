package com.byy.meterreading.auth.device;

import java.security.Principal;

/**
 * 已通过认证的设备身份；不保存设备明文密钥或密钥摘要。
 */
public record DevicePrincipal(
        Long deviceId,
        String deviceNo,
        Integer credentialVersion
) implements Principal {

    @Override
    public String getName() {
        return deviceNo;
    }
}
