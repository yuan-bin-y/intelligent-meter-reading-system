package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.audit.OperationAudit;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.chat.ChatConversationPageQueryDTO;
import com.byy.meterreading.dto.chat.ClaimChatConversationDTO;
import com.byy.meterreading.dto.chat.TransferChatConversationDTO;
import com.byy.meterreading.service.ChatService;
import com.byy.meterreading.vo.chat.ChatConversationListVO;
import com.byy.meterreading.vo.chat.ChatConversationVO;
import com.byy.meterreading.vo.common.PageVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员认领、转交和统一查询客服会话的接口。 */
@RestController
@RequestMapping("/api/v1/admin/chat/conversations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {

    private final ChatService chatService;

    public AdminChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 分页查询尚未被管理员认领的客服会话。 */
    @GetMapping("/waiting")
    public Result<PageVO<ChatConversationListVO>> listWaitingConversations(
            @Valid @ModelAttribute ChatConversationPageQueryDTO queryDTO
    ) {
        return Result.success(
                chatService.listWaitingAdminConversations(queryDTO)
        );
    }

    /** 当前管理员认领一条等待处理的客服会话。 */
    @OperationAudit(
            module = "客服会话",
            action = "认领客服会话",
            resourceType = "CHAT_CONVERSATION",
            resourceIdExpression = "#conversationId"
    )
    @PutMapping("/{conversationId}/claim")
    public Result<ChatConversationVO> claimConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @Valid @RequestBody ClaimChatConversationDTO claimDTO
    ) {
        return Result.success(chatService.claimConversation(
                extractUserId(jwt),
                conversationId,
                claimDTO
        ));
    }

    /** 当前管理员将自己处理中的客服会话转交给另一名管理员。 */
    @OperationAudit(
            module = "客服会话",
            action = "转交客服会话",
            resourceType = "CHAT_CONVERSATION",
            resourceIdExpression = "#conversationId"
    )
    @PutMapping("/{conversationId}/transfer")
    public Result<ChatConversationVO> transferConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId,
            @Valid @RequestBody TransferChatConversationDTO transferDTO
    ) {
        return Result.success(chatService.transferConversation(
                extractUserId(jwt),
                conversationId,
                transferDTO
        ));
    }

    /** 按类型和状态分页查询系统中的全部聊天会话。 */
    @GetMapping
    public Result<PageVO<ChatConversationListVO>> listConversations(
            @Valid @ModelAttribute ChatConversationPageQueryDTO queryDTO
    ) {
        return Result.success(
                chatService.listAdminConversations(queryDTO)
        );
    }

    /** 从已经通过认证的管理员 JWT 中读取用户 ID。 */
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
