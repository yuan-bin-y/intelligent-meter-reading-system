package com.byy.meterreading.web.controller.chat;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.chat.ChatConversationPageQueryDTO;
import com.byy.meterreading.dto.chat.ChatMessageCursorQueryDTO;
import com.byy.meterreading.dto.chat.CloseChatConversationDTO;
import com.byy.meterreading.dto.chat.CreateAdminSupportConversationDTO;
import com.byy.meterreading.dto.chat.CreateTaskConversationDTO;
import com.byy.meterreading.dto.chat.ReadChatMessageDTO;
import com.byy.meterreading.service.ChatService;
import com.byy.meterreading.vo.chat.ChatConversationDetailVO;
import com.byy.meterreading.vo.chat.ChatConversationListVO;
import com.byy.meterreading.vo.chat.ChatConversationVO;
import com.byy.meterreading.vo.chat.ChatMessagePageVO;
import com.byy.meterreading.vo.chat.ChatReadReceiptVO;
import com.byy.meterreading.vo.chat.ChatUnreadCountVO;
import com.byy.meterreading.vo.common.PageVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已登录用户创建和查看本人聊天会话的 REST 接口。 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 居民或抄表员针对有权访问的抄表任务创建沟通会话。 */
    @PostMapping("/conversations/task")
    @PreAuthorize("hasAnyRole('RESIDENT', 'METER_READER')")
    public Result<ChatConversationVO> createTaskConversation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTaskConversationDTO createDTO
    ) {
        return Result.success(chatService.createTaskConversation(
                extractUserId(jwt),
                createDTO
        ));
    }

    /** 居民创建等待管理员认领的客服会话，并保存首条消息。 */
    @PostMapping("/conversations/admin-support")
    @PreAuthorize("hasRole('RESIDENT')")
    public Result<ChatConversationVO> createAdminSupportConversation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAdminSupportConversationDTO createDTO
    ) {
        return Result.success(chatService.createAdminSupportConversation(
                extractUserId(jwt),
                createDTO
        ));
    }

    /** 分页查询当前用户仍有权访问的会话。 */
    @GetMapping("/conversations")
    @PreAuthorize("hasAnyRole('RESIDENT', 'METER_READER', 'ADMIN')")
    public Result<PageVO<ChatConversationListVO>> listMyConversations(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute ChatConversationPageQueryDTO queryDTO
    ) {
        return Result.success(chatService.listMyConversations(
                extractUserId(jwt),
                queryDTO
        ));
    }

    /** 查询会话详情；Service 必须校验当前用户是有效会话成员。 */
    @GetMapping("/conversations/{conversationId}")
    @PreAuthorize("hasAnyRole('RESIDENT', 'METER_READER', 'ADMIN')")
    public Result<ChatConversationDetailVO> getConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId
    ) {
        return Result.success(chatService.getConversation(
                extractUserId(jwt),
                conversationId
        ));
    }

    /** 使用消息主键游标向前分页读取历史消息。 */
    @GetMapping("/conversations/{conversationId}/messages")
    @PreAuthorize("hasAnyRole('RESIDENT', 'METER_READER', 'ADMIN')")
    public Result<ChatMessagePageVO> listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @Valid @ModelAttribute ChatMessageCursorQueryDTO queryDTO
    ) {
        return Result.success(chatService.listMessages(
                extractUserId(jwt),
                conversationId,
                queryDTO
        ));
    }

    /** 将当前成员的读取进度推进到指定消息。 */
    @PutMapping("/conversations/{conversationId}/read")
    @PreAuthorize("hasAnyRole('RESIDENT', 'METER_READER', 'ADMIN')")
    public Result<ChatReadReceiptVO> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @Valid @RequestBody ReadChatMessageDTO readDTO
    ) {
        return Result.success(chatService.markRead(
                extractUserId(jwt),
                conversationId,
                readDTO
        ));
    }

    /** 查询当前用户的未读消息数和存在未读消息的会话数。 */
    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('RESIDENT', 'METER_READER', 'ADMIN')")
    public Result<ChatUnreadCountVO> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return Result.success(
                chatService.getUnreadCount(extractUserId(jwt))
        );
    }

    /** 关闭当前用户参与的会话，版本号用于防止并发重复关闭。 */
    @PutMapping("/conversations/{conversationId}/close")
    @PreAuthorize("hasAnyRole('RESIDENT', 'METER_READER', 'ADMIN')")
    public Result<ChatConversationVO> closeConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @Valid @RequestBody CloseChatConversationDTO closeDTO
    ) {
        return Result.success(chatService.closeConversation(
                extractUserId(jwt),
                conversationId,
                closeDTO
        ));
    }

    /** 从已经通过认证的 JWT 中读取当前用户 ID。 */
    private Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number userId)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT 中缺少有效的 userId"
            );
        }
        return userId.longValue();
    }
}
