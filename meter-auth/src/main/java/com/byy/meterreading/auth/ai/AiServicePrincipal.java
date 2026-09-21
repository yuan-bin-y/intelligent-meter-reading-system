package com.byy.meterreading.auth.ai;

import java.security.Principal;

/** 已通过签名认证的 AI 视觉服务身份。 */
public record AiServicePrincipal(String serviceId) implements Principal {

    @Override
    public String getName() {
        return serviceId;
    }
}
