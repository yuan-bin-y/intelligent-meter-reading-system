package com.byy.meterreading.config;

import com.byy.meterreading.websocket.WebSocketJwtChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 聊天模块 STOMP over WebSocket 基础配置。
 *
 * <p>本类只建立连接端点和消息地址规则。JWT 身份认证将在后续通过
 * ChannelInterceptor 接入客户端入站消息通道。</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOriginPatterns;
    private final WebSocketJwtChannelInterceptor jwtChannelInterceptor;

    public WebSocketConfig(
            @Value("${app.websocket.allowed-origin-patterns}")
            String[] allowedOriginPatterns,
            WebSocketJwtChannelInterceptor jwtChannelInterceptor
    ) {
        this.allowedOriginPatterns = allowedOriginPatterns.clone();
        this.jwtChannelInterceptor = jwtChannelInterceptor;
    }

    /**
     * 注册原生 WebSocket 握手地址。
     * 前端后续通过 ws://host:port/ws/chat 建立连接并使用 STOMP 通信。
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    /**
     * /app 表示客户端发送给后端的方法；/user/queue 表示后端向指定用户推送。
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue");
        registry.setPreservePublishOrder(true);
    }

    /** 所有 STOMP 入站帧先经过 JWT 身份认证和连接状态检查。 */
    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
