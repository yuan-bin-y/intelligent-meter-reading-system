package com.byy.meterreading.auth.ai;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * AI 服务使用的 Spring Security 认证对象。
 * 未认证时保存服务编号和请求签名，认证成功后只保留服务身份。
 */
public final class AiServiceAuthenticationToken
        extends AbstractAuthenticationToken {

    private static final String AI_SERVICE_AUTHORITY =
            "ROLE_AI_SERVICE";

    private final Object principal;
    private Object credentials;

    private AiServiceAuthenticationToken(
            String serviceId,
            AiServiceRequestCredentials credentials
    ) {
        super(List.of());
        this.principal = serviceId;
        this.credentials = credentials;
        super.setAuthenticated(false);
    }

    private AiServiceAuthenticationToken(AiServicePrincipal principal) {
        super(List.of(new SimpleGrantedAuthority(AI_SERVICE_AUTHORITY)));
        this.principal = principal;
        this.credentials = null;
        super.setAuthenticated(true);
    }

    public static AiServiceAuthenticationToken unauthenticated(
            String serviceId,
            AiServiceRequestCredentials credentials
    ) {
        return new AiServiceAuthenticationToken(serviceId, credentials);
    }

    public static AiServiceAuthenticationToken authenticated(
            AiServicePrincipal principal
    ) {
        return new AiServiceAuthenticationToken(principal);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    /** 认证完成后立即清除签名、随机数及请求摘要。 */
    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        credentials = null;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException(
                    "不能直接将AI服务认证标记为成功"
            );
        }
        super.setAuthenticated(false);
    }
}
