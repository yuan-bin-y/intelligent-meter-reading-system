package com.byy.meterreading.web.controller.chat;

import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.common.result.ApiErrorCode;
import com.byy.meterreading.dto.chat.SendChatMessageDTO;
import com.byy.meterreading.dto.chat.WebSocketReadChatMessageDTO;
import com.byy.meterreading.service.ChatMessageService;
import com.byy.meterreading.vo.chat.ChatWebSocketErrorVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

/**
 * 聊天消息和已读回执的 STOMP 消息入口。
 *
 * <p>客户端实际发送地址分别为 /app/chat.send 和 /app/chat.read。
 * senderId 始终取自通过 JWT 认证的 WebSocket Principal，前端不能指定。</p>
 */
@Controller
public class ChatWebSocketController {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ChatWebSocketController.class
    );

    private final ChatMessageService chatMessageService;

    public ChatWebSocketController(
            ChatMessageService chatMessageService
    ) {
        this.chatMessageService = chatMessageService;
    }

    /** 接收并持久化一条实时聊天消息。 */
    @MessageMapping("/chat.send")
    public void sendMessage(
            Principal principal,
            @Valid @Payload SendChatMessageDTO sendDTO
    ) {
        chatMessageService.sendMessage(
                extractUserId(principal),
                sendDTO
        );
    }

    /** 推进当前用户在会话中的最后已读消息位置。 */
    @MessageMapping("/chat.read")
    public void markRead(
            Principal principal,
            @Valid @Payload WebSocketReadChatMessageDTO readDTO
    ) {
        chatMessageService.markRead(
                extractUserId(principal),
                readDTO
        );
    }

    /**
     * 将 STOMP 消息处理阶段的异常转换成当前用户可订阅的结构化错误帧。
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/chat.errors")
    public ChatWebSocketErrorVO handleMessageException(
            Exception exception
    ) {
        if (exception instanceof MethodArgumentNotValidException validation) {
            String message = validation.getBindingResult().getFieldError()
                    == null
                    ? "消息参数不正确"
                    : validation.getBindingResult().getFieldError()
                    .getDefaultMessage();
            return error(ApiErrorCode.BAD_REQUEST, message);
        }
        if (exception instanceof AuthenticationException) {
            return error(ApiErrorCode.AUTH_REQUIRED, exception.getMessage());
        }
        if (exception instanceof AccessDeniedException) {
            return error(ApiErrorCode.FORBIDDEN, exception.getMessage());
        }
        if (exception instanceof ResourceNotFoundException) {
            return error(
                    ApiErrorCode.RESOURCE_NOT_FOUND,
                    exception.getMessage()
            );
        }
        if (exception instanceof VersionConflictException) {
            return error(
                    ApiErrorCode.VERSION_CONFLICT,
                    exception.getMessage()
            );
        }
        if (exception instanceof ResourceConflictException) {
            return error(
                    ApiErrorCode.RESOURCE_CONFLICT,
                    exception.getMessage()
            );
        }
        if (exception instanceof IllegalArgumentException) {
            return error(ApiErrorCode.BAD_REQUEST, exception.getMessage());
        }

        LOGGER.error("聊天 WebSocket 消息处理失败", exception);
        return error(ApiErrorCode.INTERNAL_ERROR, "服务器内部错误");
    }

    private ChatWebSocketErrorVO error(
            ApiErrorCode code,
            String message
    ) {
        return new ChatWebSocketErrorVO(
                code.code(),
                message == null || message.isBlank()
                        ? code.getMessage()
                        : message,
                LocalDateTime.now()
        );
    }

    /**
     * JWT 拦截器已经将 Principal 名称设置为用户 ID，这里只负责安全转换。
     */
    private Long extractUserId(Principal principal) {
        if (principal == null
                || principal.getName() == null
                || principal.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "WebSocket 连接缺少已认证用户"
            );
        }
        try {
            long userId = Long.parseLong(principal.getName());
            if (userId <= 0) {
                throw new NumberFormatException("用户ID必须大于0");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "WebSocket 用户身份不合法",
                    exception
            );
        }
    }
}
