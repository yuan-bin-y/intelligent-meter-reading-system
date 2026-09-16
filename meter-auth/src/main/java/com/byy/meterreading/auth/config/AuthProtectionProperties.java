package com.byy.meterreading.auth.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 登录、注册限流以及登录失败锁定配置。
 */
@Validated
@ConfigurationProperties(prefix = "app.security.auth-protection")
public record AuthProtectionProperties(
        @Positive(message = "每分钟登录请求上限必须大于 0")
        int loginPerMinute,

        @Positive(message = "每小时注册请求上限必须大于 0")
        int registerPerHour,

        @Positive(message = "登录失败次数上限必须大于 0")
        int maxLoginFailures,

        @NotNull(message = "登录失败统计窗口不能为空")
        Duration loginFailureWindow,

        @NotNull(message = "登录锁定时间不能为空")
        Duration loginLockDuration
) {

    public AuthProtectionProperties {
        if (loginFailureWindow != null
                && (loginFailureWindow.isZero()
                || loginFailureWindow.isNegative())) {
            throw new IllegalArgumentException(
                    "登录失败统计窗口必须大于 0"
            );
        }
        if (loginLockDuration != null
                && (loginLockDuration.isZero()
                || loginLockDuration.isNegative())) {
            throw new IllegalArgumentException(
                    "登录锁定时间必须大于 0"
            );
        }
    }
}
