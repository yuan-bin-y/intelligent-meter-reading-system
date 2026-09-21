package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.dto.chat.ReadChatMessageDTO;
import com.byy.meterreading.dto.chat.SendChatMessageDTO;
import com.byy.meterreading.dto.chat.WebSocketReadChatMessageDTO;
import com.byy.meterreading.mapper.ChatConversationMapper;
import com.byy.meterreading.mapper.ChatConversationMemberMapper;
import com.byy.meterreading.mapper.ChatMessageMapper;
import com.byy.meterreading.model.ChatConversation;
import com.byy.meterreading.model.ChatConversationMember;
import com.byy.meterreading.model.ChatMessage;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.model.enums.ChatConversationStatus;
import com.byy.meterreading.model.enums.ChatMemberRole;
import com.byy.meterreading.model.enums.ChatMessageSenderRole;
import com.byy.meterreading.model.enums.ChatMessageType;
import com.byy.meterreading.service.ChatMessageService;
import com.byy.meterreading.service.ChatService;
import com.byy.meterreading.service.SysUserService;
import com.byy.meterreading.service.event.ChatMessageSentEvent;
import com.byy.meterreading.service.event.ChatReadReceiptEvent;
import com.byy.meterreading.vo.chat.ChatMessageVO;
import com.byy.meterreading.vo.chat.ChatReadReceiptVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** WebSocket 实时消息持久化、幂等处理和已读回执实现。 */
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatConversationMapper conversationMapper;
    private final ChatConversationMemberMapper memberMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatService chatService;
    private final SysUserService sysUserService;
    private final ApplicationEventPublisher eventPublisher;

    public ChatMessageServiceImpl(
            ChatConversationMapper conversationMapper,
            ChatConversationMemberMapper memberMapper,
            ChatMessageMapper messageMapper,
            ChatService chatService,
            SysUserService sysUserService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.chatService = chatService;
        this.sysUserService = sysUserService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 锁定会话后完成权限校验、消息幂等保存和最后消息更新。
     * 这里只发布 Spring 事件，真正的 WebSocket 推送在事务提交后执行。
     */
    @Override
    @Transactional
    public ChatMessageVO sendMessage(
            Long senderId,
            SendChatMessageDTO sendDTO
    ) {
        requirePositiveId(senderId, "发送人ID不合法");
        ChatConversation conversation = requireActiveConversationForUpdate(
                sendDTO.conversationId()
        );
        ChatConversationMember senderMember = requireActiveMember(
                conversation.getId(),
                senderId
        );

        ChatMessage existing = findByClientMessageId(
                conversation.getId(),
                sendDTO.clientMessageId()
        );
        if (existing != null) {
            requireSameSender(existing, senderId);
            ChatMessageVO existingVO = toMessageVO(existing);
            // 网络重发只向发送者补发确认，避免其他成员重复收到同一消息。
            eventPublisher.publishEvent(new ChatMessageSentEvent(
                    existingVO,
                    List.of(senderId)
            ));
            return existingVO;
        }

        ChatMemberRole memberRole = ChatMemberRole.valueOf(
                senderMember.getMemberRole()
        );
        ChatMessageSenderRole senderRole =
                ChatMessageSenderRole.fromMemberRole(memberRole);
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = ChatMessage.builder()
                .conversationId(conversation.getId())
                .clientMessageId(sendDTO.clientMessageId())
                .senderId(senderId)
                .senderRole(senderRole.name())
                .messageType(ChatMessageType.TEXT.name())
                .content(sendDTO.content())
                .deleted(0)
                .createdAt(now)
                .build();
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException exception) {
            ChatMessage concurrent = findByClientMessageId(
                    conversation.getId(),
                    sendDTO.clientMessageId()
            );
            if (concurrent == null) {
                throw new ResourceConflictException(
                        "消息已被并发提交，请勿重复发送",
                        exception
                );
            }
            requireSameSender(concurrent, senderId);
            ChatMessageVO concurrentVO = toMessageVO(concurrent);
            eventPublisher.publishEvent(new ChatMessageSentEvent(
                    concurrentVO,
                    List.of(senderId)
            ));
            return concurrentVO;
        }

        int updated = conversationMapper.update(
                null,
                Wrappers.<ChatConversation>lambdaUpdate()
                        .set(ChatConversation::getLastMessageId,
                                message.getId())
                        .set(ChatConversation::getLastMessageAt, now)
                        .set(ChatConversation::getUpdatedAt, now)
                        .eq(ChatConversation::getId, conversation.getId())
                        .eq(ChatConversation::getStatus,
                                ChatConversationStatus.ACTIVE.name())
        );
        if (updated != 1) {
            throw new ResourceConflictException("会话已关闭，不能继续发送消息");
        }

        ChatMessageVO messageVO = toMessageVO(message);
        eventPublisher.publishEvent(new ChatMessageSentEvent(
                messageVO,
                selectActiveMemberIds(conversation.getId())
        ));
        return messageVO;
    }

    /** 复用 REST 会话业务的已读校验和原子游标更新，再发布实时回执。 */
    @Override
    @Transactional
    public ChatReadReceiptVO markRead(
            Long currentUserId,
            WebSocketReadChatMessageDTO readDTO
    ) {
        ChatReadReceiptVO receipt = chatService.markRead(
                currentUserId,
                readDTO.conversationId(),
                new ReadChatMessageDTO(readDTO.lastReadMessageId())
        );
        eventPublisher.publishEvent(new ChatReadReceiptEvent(
                receipt,
                selectActiveMemberIds(readDTO.conversationId())
        ));
        return receipt;
    }

    private ChatConversation requireActiveConversationForUpdate(
            Long conversationId
    ) {
        requirePositiveId(conversationId, "会话ID必须大于0");
        ChatConversation conversation =
                conversationMapper.selectByIdForUpdate(conversationId);
        if (conversation == null) {
            throw new ResourceNotFoundException("聊天会话不存在");
        }
        if (!ChatConversationStatus.ACTIVE.name().equals(
                conversation.getStatus())) {
            throw new IllegalArgumentException("当前会话状态不允许发送消息");
        }
        return conversation;
    }

    private ChatConversationMember requireActiveMember(
            Long conversationId,
            Long userId
    ) {
        ChatConversationMember member = memberMapper.selectOne(
                Wrappers.<ChatConversationMember>lambdaQuery()
                        .eq(ChatConversationMember::getConversationId,
                                conversationId)
                        .eq(ChatConversationMember::getUserId, userId)
                        .isNull(ChatConversationMember::getLeftAt)
                        .last("LIMIT 1")
        );
        if (member == null) {
            throw new ResourceNotFoundException("聊天会话不存在或无权访问");
        }
        return member;
    }

    private ChatMessage findByClientMessageId(
            Long conversationId,
            String clientMessageId
    ) {
        return messageMapper.selectOne(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .eq(ChatMessage::getClientMessageId,
                                clientMessageId)
                        .last("LIMIT 1")
        );
    }

    private void requireSameSender(ChatMessage message, Long senderId) {
        if (!senderId.equals(message.getSenderId())) {
            throw new ResourceConflictException(
                    "clientMessageId 已被其他发送者使用"
            );
        }
    }

    private List<Long> selectActiveMemberIds(Long conversationId) {
        return memberMapper.selectList(
                        Wrappers.<ChatConversationMember>lambdaQuery()
                                .select(ChatConversationMember::getUserId)
                                .eq(ChatConversationMember::getConversationId,
                                        conversationId)
                                .isNull(ChatConversationMember::getLeftAt)
                                .orderByAsc(ChatConversationMember::getUserId)
                ).stream()
                .map(ChatConversationMember::getUserId)
                .distinct()
                .toList();
    }

    private ChatMessageVO toMessageVO(ChatMessage message) {
        ChatMessageSenderRole senderRole =
                ChatMessageSenderRole.valueOf(message.getSenderRole());
        ChatMessageType messageType = ChatMessageType.valueOf(
                message.getMessageType()
        );
        SysUser sender = message.getSenderId() == null
                ? null
                : sysUserService.findById(message.getSenderId());
        String senderDisplayName = senderRole
                == ChatMessageSenderRole.SYSTEM
                ? "系统"
                : sender == null ? null : sender.getDisplayName();
        boolean deleted = Integer.valueOf(1).equals(message.getDeleted());
        return new ChatMessageVO(
                message.getId(),
                message.getConversationId(),
                message.getClientMessageId(),
                message.getSenderId(),
                senderDisplayName,
                senderRole,
                senderRole.getDescription(),
                messageType,
                messageType.getDescription(),
                deleted ? "[消息已删除]" : message.getContent(),
                deleted,
                message.getCreatedAt()
        );
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
