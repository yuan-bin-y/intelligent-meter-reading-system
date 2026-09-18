package com.byy.meterreading.auth.device;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 设备接口认证过滤器，从请求头提取设备编号和设备密钥并交给认证管理器。
 */
@Component
public class DeviceAuthenticationFilter extends OncePerRequestFilter {

    public static final String DEVICE_NO_HEADER = "X-Device-No";
    public static final String DEVICE_SECRET_HEADER = "X-Device-Secret";

    private static final String DEVICE_API_PREFIX = "/api/v1/device/";

    private final AuthenticationManager authenticationManager;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;

    public DeviceAuthenticationFilter(
            AuthenticationManager authenticationManager,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationManager = authenticationManager;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String deviceNo = request.getHeader(DEVICE_NO_HEADER);
        String deviceSecret = request.getHeader(DEVICE_SECRET_HEADER);
        if (!StringUtils.hasText(deviceNo)
                || !StringUtils.hasText(deviceSecret)) {
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("缺少设备认证凭证")
            );
            return;
        }

        DeviceAuthenticationToken authenticationRequest =
                DeviceAuthenticationToken.unauthenticated(
                        deviceNo.trim(),
                        deviceSecret
                );
        authenticationRequest.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        try {
            Authentication authenticated = authenticationManager.authenticate(
                    authenticationRequest
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (DisabledException exception) {
            SecurityContextHolder.clearContext();
            accessDeniedHandler.handle(
                    request,
                    response,
                    new AccessDeniedException(exception.getMessage(), exception)
            );
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, exception);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getRequestURI()
                .substring(request.getContextPath().length());
        return !requestPath.startsWith(DEVICE_API_PREFIX);
    }
}
