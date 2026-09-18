package com.byy.meterreading.auth.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.byy.meterreading.mapper.DeviceMapper;
import com.byy.meterreading.model.Device;
import com.byy.meterreading.model.enums.DeviceStatus;
import com.byy.meterreading.service.DeviceCredentialService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * 根据设备编号和设备密钥校验设备身份。
 */
@Component
public class DeviceAuthenticationProvider
        implements AuthenticationProvider {

    private static final String BAD_CREDENTIALS_MESSAGE =
            "设备编号或密钥错误";

    private final DeviceMapper deviceMapper;
    private final DeviceCredentialService deviceCredentialService;

    public DeviceAuthenticationProvider(
            DeviceMapper deviceMapper,
            DeviceCredentialService deviceCredentialService
    ) {
        this.deviceMapper = deviceMapper;
        this.deviceCredentialService = deviceCredentialService;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        if (!(authentication instanceof DeviceAuthenticationToken token)) {
            return null;
        }

        try {
            Object principal = token.getPrincipal();
            Object credentials = token.getCredentials();
            if (!(principal instanceof String deviceNo)
                    || !(credentials instanceof String deviceSecret)
                    || deviceNo.isBlank()
                    || deviceSecret.isBlank()) {
                throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
            }

            Device device = deviceMapper.selectOne(
                    Wrappers.<Device>lambdaQuery()
                            .eq(Device::getDeviceNo, deviceNo)
            );
            if (device == null
                    || !deviceCredentialService.matches(
                            deviceSecret,
                            device.getSecretHash()
                    )) {
                // 不区分设备不存在、未配置密钥和密钥错误，避免泄露设备信息。
                throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
            }

            if (!Integer.valueOf(DeviceStatus.ENABLED.getCode())
                    .equals(device.getStatus())) {
                throw new DisabledException("设备已停用");
            }

            DevicePrincipal devicePrincipal = new DevicePrincipal(
                    device.getId(),
                    device.getDeviceNo(),
                    device.getCredentialVersion()
            );
            DeviceAuthenticationToken authenticated =
                    DeviceAuthenticationToken.authenticated(devicePrincipal);
            authenticated.setDetails(token.getDetails());
            return authenticated;
        } finally {
            // 无论认证成功还是失败，都尽快清除 Token 中的明文设备密钥。
            token.eraseCredentials();
        }
    }

    @Override
    public boolean supports(Class<?> authenticationType) {
        return DeviceAuthenticationToken.class
                .isAssignableFrom(authenticationType);
    }
}
