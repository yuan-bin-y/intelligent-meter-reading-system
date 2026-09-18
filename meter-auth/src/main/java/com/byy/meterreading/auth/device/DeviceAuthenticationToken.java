package com.byy.meterreading.auth.device;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * 设备请求使用的 Spring Security 认证对象。
 * 未认证时暂存设备编号和明文密钥，认证成功后只保留 DevicePrincipal。
 */
public final class DeviceAuthenticationToken
        extends AbstractAuthenticationToken {

    private static final String DEVICE_AUTHORITY = "ROLE_DEVICE";

    private final Object principal;
    private Object credentials;

    private DeviceAuthenticationToken(
            String deviceNo,
            String deviceSecret
    ) {
        super(List.of());
        this.principal = deviceNo;
        this.credentials = deviceSecret;
        super.setAuthenticated(false);
    }

    private DeviceAuthenticationToken(DevicePrincipal principal) {
        super(List.of(new SimpleGrantedAuthority(DEVICE_AUTHORITY)));
        this.principal = principal;
        this.credentials = null;
        super.setAuthenticated(true);
    }

    /**
     * 由设备认证过滤器创建，交给 AuthenticationManager 校验。
     */
    public static DeviceAuthenticationToken unauthenticated(
            String deviceNo,
            String deviceSecret
    ) {
        return new DeviceAuthenticationToken(deviceNo, deviceSecret);
    }

    /**
     * 只能由认证提供器在密钥校验成功后创建。
     */
    public static DeviceAuthenticationToken authenticated(
            DevicePrincipal principal
    ) {
        return new DeviceAuthenticationToken(principal);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    /**
     * 认证完成后清除内存中的明文设备密钥。
     */
    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        credentials = null;
    }

    /**
     * 禁止调用方直接把未认证 Token 标记为已认证。
     */
    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException(
                    "不能直接将设备认证标记为成功"
            );
        }
        super.setAuthenticated(false);
    }
}
