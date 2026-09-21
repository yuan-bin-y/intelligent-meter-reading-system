package com.byy.meterreading.websocket;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * STOMP 入站消息的 JWT 身份认证拦截器。
 *
 * <p>HTTP 层允许匿名完成 WebSocket 握手，真正的用户认证发生在 STOMP
 * CONNECT 帧中。这里复用 REST 使用的 JwtDecoder，因此会同时校验 JWT
 * 签名、签发方、有效期、Access Token 类型以及 Redis 登录会话。</p>
 */
@Component
public class WebSocketJwtChannelInterceptor
        implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter authenticationConverter;

    public WebSocketJwtChannelInterceptor(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter authenticationConverter
    ) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            authenticateConnect(accessor);
        } else if (requiresAuthentication(command)
                && accessor.getUser() == null) {
            throw new AccessDeniedException("WebSocket 连接尚未完成身份认证");
        }

        return message;
    }

    /** 从 STOMP 原生请求头读取 Access Token，并建立当前连接的用户身份。 */
    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(
                HttpHeaders.AUTHORIZATION
        );
        // 部分 STOMP 客户端会将自定义请求头名称转成小写。
        if (authorization == null) {
            authorization = accessor.getFirstNativeHeader("authorization");
        }
        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException(
                    "WebSocket CONNECT 缺少 Bearer Access Token"
            );
        }

        String token = authorization.substring(BEARER_PREFIX.length())
                .trim();
        if (token.isEmpty()) {
            throw new BadCredentialsException(
                    "WebSocket Access Token 不能为空"
            );
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException exception) {
            throw new BadCredentialsException(
                    "WebSocket Access Token 无效或已过期",
                    exception
            );
        }

        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number userId)) {
            throw new BadCredentialsException(
                    "WebSocket Access Token 缺少有效的 userId"
            );
        }

        var converted = authenticationConverter.convert(jwt);
        if (converted == null) {
            throw new BadCredentialsException(
                    "WebSocket Access Token 无法转换为认证身份"
            );
        }

        // Principal 名称使用用户 ID，后续 convertAndSendToUser 可以直接按 ID 推送。
        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                        jwt,
                        converted.getAuthorities(),
                        Long.toString(userId.longValue())
                );
        accessor.setUser(authentication);
    }

    /** SEND 和 SUBSCRIBE 会触发业务或接收消息，必须已经通过 CONNECT 认证。 */
    private boolean requiresAuthentication(StompCommand command) {
        return StompCommand.SEND.equals(command)
                || StompCommand.SUBSCRIBE.equals(command)
                || StompCommand.UNSUBSCRIBE.equals(command)
                || StompCommand.ACK.equals(command)
                || StompCommand.NACK.equals(command);
    }
}
