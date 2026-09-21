package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.dto.chat.ChatConversationPageQueryDTO;
import com.byy.meterreading.dto.chat.ChatMessageCursorQueryDTO;
import com.byy.meterreading.dto.chat.ClaimChatConversationDTO;
import com.byy.meterreading.dto.chat.CloseChatConversationDTO;
import com.byy.meterreading.dto.chat.CreateAdminSupportConversationDTO;
import com.byy.meterreading.dto.chat.CreateTaskConversationDTO;
import com.byy.meterreading.dto.chat.ReadChatMessageDTO;
import com.byy.meterreading.dto.chat.TransferChatConversationDTO;
import com.byy.meterreading.mapper.ChatConversationMapper;
import com.byy.meterreading.mapper.ChatConversationMemberMapper;
import com.byy.meterreading.mapper.ChatMessageMapper;
import com.byy.meterreading.mapper.MeterReadingTaskMapper;
import com.byy.meterreading.mapper.ResidentMeterMapper;
import com.byy.meterreading.model.ChatConversation;
import com.byy.meterreading.model.ChatConversationMember;
import com.byy.meterreading.model.ChatMessage;
import com.byy.meterreading.model.MeterReadingTask;
import com.byy.meterreading.model.ResidentMeter;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.model.enums.ChatConversationStatus;
import com.byy.meterreading.model.enums.ChatConversationType;
import com.byy.meterreading.model.enums.ChatMemberRole;
import com.byy.meterreading.model.enums.ChatMessageSenderRole;
import com.byy.meterreading.model.enums.ChatMessageType;
import com.byy.meterreading.model.enums.TaskExecutorType;
import com.byy.meterreading.service.ChatService;
import com.byy.meterreading.service.SysUserService;
import com.byy.meterreading.service.event.ChatMessageSentEvent;
import com.byy.meterreading.vo.chat.ChatConversationDetailVO;
import com.byy.meterreading.vo.chat.ChatConversationListVO;
import com.byy.meterreading.vo.chat.ChatConversationVO;
import com.byy.meterreading.vo.chat.ChatMessagePageVO;
import com.byy.meterreading.vo.chat.ChatMessageVO;
import com.byy.meterreading.vo.chat.ChatReadReceiptVO;
import com.byy.meterreading.vo.chat.ChatUnreadCountVO;
import com.byy.meterreading.vo.common.PageVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 聊天会话、成员权限、历史消息和管理员客服流转的业务实现。
 *
 * <p>实时消息发送后续由 ChatMessageService 处理，但会继续复用本实现
 * 使用的三张聊天表和成员权限规则。</p>
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final String ROLE_RESIDENT = "RESIDENT";
    private static final String ROLE_METER_READER = "METER_READER";
    private static final String ROLE_ADMIN = "ADMIN";

    private final ChatConversationMapper conversationMapper;
    private final ChatConversationMemberMapper memberMapper;
    private final ChatMessageMapper messageMapper;
    private final MeterReadingTaskMapper readingTaskMapper;
    private final ResidentMeterMapper residentMeterMapper;
    private final SysUserService sysUserService;
    private final ApplicationEventPublisher eventPublisher;

    public ChatServiceImpl(
            ChatConversationMapper conversationMapper,
            ChatConversationMemberMapper memberMapper,
            ChatMessageMapper messageMapper,
            MeterReadingTaskMapper readingTaskMapper,
            ResidentMeterMapper residentMeterMapper,
            SysUserService sysUserService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.readingTaskMapper = readingTaskMapper;
        this.residentMeterMapper = residentMeterMapper;
        this.sysUserService = sysUserService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 人工抄表任务只建立一个沟通会话，并把该表具居民和任务抄表员加入会话。
     */
    @Override
    @Transactional
    public ChatConversationVO createTaskConversation(
            Long currentUserId,
            CreateTaskConversationDTO createDTO
    ) {
        requirePositiveId(currentUserId, "当前用户ID不合法");
        MeterReadingTask task = requireReadingTask(createDTO.taskId());
        requireManualTask(task);

        List<Long> residentIds = selectResidentIds(task.getMeterId());
        if (residentIds.isEmpty()) {
            throw new IllegalArgumentException("任务表具尚未绑定居民");
        }
        requireTaskParticipant(currentUserId, task, residentIds);

        ChatConversation existing = conversationMapper.selectByTaskId(
                ChatConversationType.TASK_SERVICE.name(),
                task.getId()
        );
        if (existing != null) {
            return toConversationVO(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = ChatConversation.builder()
                .conversationType(ChatConversationType.TASK_SERVICE.name())
                .taskId(task.getId())
                .subject("抄表任务 " + task.getTaskNo() + " 沟通")
                .status(ChatConversationStatus.ACTIVE.name())
                .createdBy(currentUserId)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            conversationMapper.insert(conversation);
            for (Long residentId : residentIds) {
                saveMember(
                        conversation.getId(),
                        residentId,
                        ChatMemberRole.RESIDENT,
                        now
                );
            }
            saveMember(
                    conversation.getId(),
                    task.getMeterReaderId(),
                    ChatMemberRole.METER_READER,
                    now
            );
        } catch (DuplicateKeyException exception) {
            ChatConversation concurrent = conversationMapper.selectByTaskId(
                    ChatConversationType.TASK_SERVICE.name(),
                    task.getId()
            );
            if (concurrent != null) {
                return toConversationVO(concurrent);
            }
            throw new ResourceConflictException(
                    "该任务的沟通会话已被并发创建",
                    exception
            );
        }
        return toConversationVO(conversation);
    }

    /** 创建等待认领的客服会话，并在同一事务内写入居民首条消息。 */
    @Override
    @Transactional
    public ChatConversationVO createAdminSupportConversation(
            Long residentId,
            CreateAdminSupportConversationDTO createDTO
    ) {
        requireEnabledUserWithRole(residentId, ROLE_RESIDENT, "居民");
        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = ChatConversation.builder()
                .conversationType(ChatConversationType.ADMIN_SUPPORT.name())
                .subject(createDTO.subject())
                .status(ChatConversationStatus.WAITING.name())
                .createdBy(residentId)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        conversationMapper.insert(conversation);
        saveMember(
                conversation.getId(),
                residentId,
                ChatMemberRole.RESIDENT,
                now
        );
        saveTextMessage(
                conversation,
                residentId,
                ChatMessageSenderRole.RESIDENT,
                createDTO.clientMessageId(),
                createDTO.content(),
                now
        );
        return toConversationVO(conversation);
    }

    /** 当前用户只能查询自己仍为有效成员的会话。 */
    @Override
    @Transactional(readOnly = true)
    public PageVO<ChatConversationListVO> listMyConversations(
            Long currentUserId,
            ChatConversationPageQueryDTO queryDTO
    ) {
        requirePositiveId(currentUserId, "当前用户ID不合法");
        Page<ChatConversationListVO> page = new Page<>(
                queryDTO.page(), queryDTO.pageSize()
        );
        IPage<ChatConversationListVO> result =
                conversationMapper.selectMyConversationPage(
                        page,
                        currentUserId,
                        enumName(queryDTO.type()),
                        enumName(queryDTO.status())
                );
        return toPageVO(result);
    }

    /** 会话详情查询必须先通过有效成员校验。 */
    @Override
    @Transactional(readOnly = true)
    public ChatConversationDetailVO getConversation(
            Long currentUserId,
            Long conversationId
    ) {
        requireActiveMember(currentUserId, conversationId);
        ChatConversationDetailVO detail =
                conversationMapper.selectConversationDetail(conversationId);
        if (detail == null) {
            throw new ResourceNotFoundException("聊天会话不存在");
        }
        return detail;
    }

    /** 消息使用主键游标查询，避免新消息插入导致页码分页重复或遗漏。 */
    @Override
    @Transactional(readOnly = true)
    public ChatMessagePageVO listMessages(
            Long currentUserId,
            Long conversationId,
            ChatMessageCursorQueryDTO queryDTO
    ) {
        requireActiveMember(currentUserId, conversationId);
        int requestedSize = queryDTO.pageSize();
        List<ChatMessageVO> queried = new ArrayList<>(
                messageMapper.selectMessageHistory(
                        conversationId,
                        queryDTO.beforeMessageId(),
                        requestedSize + 1
                )
        );
        boolean hasMore = queried.size() > requestedSize;
        if (hasMore) {
            queried.remove(queried.size() - 1);
        }
        Long nextBeforeMessageId = hasMore && !queried.isEmpty()
                ? queried.get(queried.size() - 1).messageId()
                : null;
        // SQL 为提高游标查询效率按 ID 倒序读取，接口按时间正序返回便于展示。
        Collections.reverse(queried);
        return new ChatMessagePageVO(
                queried,
                nextBeforeMessageId,
                hasMore
        );
    }

    /** 已读游标只允许向前推进，并发旧请求不会覆盖较新的读取位置。 */
    @Override
    @Transactional
    public ChatReadReceiptVO markRead(
            Long currentUserId,
            Long conversationId,
            ReadChatMessageDTO readDTO
    ) {
        requireActiveMember(currentUserId, conversationId);
        ChatMessage message = messageMapper.selectById(
                readDTO.lastReadMessageId()
        );
        if (message == null
                || !conversationId.equals(message.getConversationId())) {
            throw new IllegalArgumentException("最后已读消息不属于当前会话");
        }

        LocalDateTime readAt = LocalDateTime.now();
        memberMapper.advanceReadCursor(
                conversationId,
                currentUserId,
                message.getId(),
                readAt
        );
        ChatConversationMember member = requireActiveMember(
                currentUserId,
                conversationId
        );
        return new ChatReadReceiptVO(
                conversationId,
                currentUserId,
                member.getLastReadMessageId(),
                member.getLastReadAt()
        );
    }

    /** 汇总当前用户所有有效会话中的他人未读消息。 */
    @Override
    @Transactional(readOnly = true)
    public ChatUnreadCountVO getUnreadCount(Long currentUserId) {
        requirePositiveId(currentUserId, "当前用户ID不合法");
        long unreadMessages = messageMapper.countUnreadMessages(currentUserId);
        long unreadConversations =
                messageMapper.countUnreadConversations(currentUserId);
        return new ChatUnreadCountVO(
                unreadMessages,
                unreadConversations
        );
    }

    /** 关闭会话时使用状态与版本双重条件，并记录一条系统消息。 */
    @Override
    @Transactional
    public ChatConversationVO closeConversation(
            Long currentUserId,
            Long conversationId,
            CloseChatConversationDTO closeDTO
    ) {
        ChatConversation conversation =
                requireConversationForUpdate(conversationId);
        requireActiveMember(currentUserId, conversationId);
        requireVersion(closeDTO.version(), conversation.getVersion());
        ChatConversationStatus currentStatus = statusOf(conversation);
        if (!currentStatus.canTransitionTo(ChatConversationStatus.CLOSED)) {
            throw new IllegalArgumentException("当前会话状态不允许关闭");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = conversationMapper.update(
                null,
                Wrappers.<ChatConversation>lambdaUpdate()
                        .set(ChatConversation::getStatus,
                                ChatConversationStatus.CLOSED.name())
                        .set(ChatConversation::getClosedBy, currentUserId)
                        .set(ChatConversation::getCloseReason,
                                closeDTO.reason())
                        .set(ChatConversation::getClosedAt, now)
                        .setSql("version = version + 1")
                        .eq(ChatConversation::getId, conversationId)
                        .eq(ChatConversation::getStatus,
                                conversation.getStatus())
                        .eq(ChatConversation::getVersion,
                                closeDTO.version())
        );
        ensureConversationUpdated(updated);
        conversation.setStatus(ChatConversationStatus.CLOSED.name());
        conversation.setClosedBy(currentUserId);
        conversation.setCloseReason(closeDTO.reason());
        conversation.setClosedAt(now);
        conversation.setVersion(conversation.getVersion() + 1);
        conversation.setUpdatedAt(now);
        saveSystemMessage(
                conversation,
                "会话已关闭：" + closeDTO.reason(),
                now
        );
        return toConversationVO(conversation);
    }

    /** 等待认领列表始终强制限定为管理员客服和 WAITING 状态。 */
    @Override
    @Transactional(readOnly = true)
    public PageVO<ChatConversationListVO> listWaitingAdminConversations(
            ChatConversationPageQueryDTO queryDTO
    ) {
        Page<ChatConversationListVO> page = new Page<>(
                queryDTO.page(), queryDTO.pageSize()
        );
        IPage<ChatConversationListVO> result =
                conversationMapper.selectAdminConversationPage(
                        page,
                        ChatConversationType.ADMIN_SUPPORT.name(),
                        ChatConversationStatus.WAITING.name()
                );
        return toPageVO(result);
    }

    /** 只有第一个匹配 WAITING 状态和客户端版本的管理员能够认领成功。 */
    @Override
    @Transactional
    public ChatConversationVO claimConversation(
            Long adminId,
            Long conversationId,
            ClaimChatConversationDTO claimDTO
    ) {
        SysUser admin = requireEnabledUserWithRole(
                adminId,
                ROLE_ADMIN,
                "管理员"
        );
        ChatConversation conversation =
                requireConversationForUpdate(conversationId);
        requireAdminSupportConversation(conversation);
        requireStatus(
                conversation,
                ChatConversationStatus.WAITING,
                "只有等待认领的客服会话可以认领"
        );
        requireVersion(claimDTO.version(), conversation.getVersion());

        LocalDateTime now = LocalDateTime.now();
        saveMember(
                conversationId,
                adminId,
                ChatMemberRole.ADMIN,
                now
        );
        int updated = conversationMapper.update(
                null,
                Wrappers.<ChatConversation>lambdaUpdate()
                        .set(ChatConversation::getStatus,
                                ChatConversationStatus.ACTIVE.name())
                        .setSql("version = version + 1")
                        .eq(ChatConversation::getId, conversationId)
                        .eq(ChatConversation::getStatus,
                                ChatConversationStatus.WAITING.name())
                        .eq(ChatConversation::getVersion,
                                claimDTO.version())
        );
        ensureConversationUpdated(updated);
        conversation.setStatus(ChatConversationStatus.ACTIVE.name());
        conversation.setVersion(conversation.getVersion() + 1);
        conversation.setUpdatedAt(now);
        saveSystemMessage(
                conversation,
                "管理员“" + admin.getDisplayName() + "”已接入会话",
                now
        );
        return toConversationVO(conversation);
    }

    /** 转交时保留原管理员成员记录，并将其 leftAt 设置为转交时间。 */
    @Override
    @Transactional
    public ChatConversationVO transferConversation(
            Long adminId,
            Long conversationId,
            TransferChatConversationDTO transferDTO
    ) {
        SysUser sourceAdmin = requireEnabledUserWithRole(
                adminId,
                ROLE_ADMIN,
                "当前管理员"
        );
        if (adminId.equals(transferDTO.targetAdminId())) {
            throw new IllegalArgumentException("不能将会话转交给自己");
        }
        SysUser targetAdmin = requireEnabledUserWithRole(
                transferDTO.targetAdminId(),
                ROLE_ADMIN,
                "目标管理员"
        );
        ChatConversation conversation =
                requireConversationForUpdate(conversationId);
        requireAdminSupportConversation(conversation);
        requireStatus(
                conversation,
                ChatConversationStatus.ACTIVE,
                "只有沟通中的客服会话可以转交"
        );
        requireVersion(transferDTO.version(), conversation.getVersion());

        ChatConversationMember sourceMember = requireActiveMember(
                adminId,
                conversationId
        );
        if (!ChatMemberRole.ADMIN.name().equals(
                sourceMember.getMemberRole())) {
            throw new ResourceNotFoundException("当前管理员无权转交该会话");
        }
        if (findActiveMember(
                conversationId,
                transferDTO.targetAdminId()
        ) != null) {
            throw new ResourceConflictException("目标管理员已在当前会话中");
        }

        LocalDateTime now = LocalDateTime.now();
        int leftRows = memberMapper.update(
                null,
                Wrappers.<ChatConversationMember>lambdaUpdate()
                        .set(ChatConversationMember::getLeftAt, now)
                        .eq(ChatConversationMember::getConversationId,
                                conversationId)
                        .eq(ChatConversationMember::getUserId, adminId)
                        .isNull(ChatConversationMember::getLeftAt)
        );
        if (leftRows != 1) {
            throw new VersionConflictException("会话处理人已发生变化，请刷新后重试");
        }
        saveMember(
                conversationId,
                transferDTO.targetAdminId(),
                ChatMemberRole.ADMIN,
                now
        );
        int updated = conversationMapper.update(
                null,
                Wrappers.<ChatConversation>lambdaUpdate()
                        .setSql("version = version + 1")
                        .eq(ChatConversation::getId, conversationId)
                        .eq(ChatConversation::getStatus,
                                ChatConversationStatus.ACTIVE.name())
                        .eq(ChatConversation::getVersion,
                                transferDTO.version())
        );
        ensureConversationUpdated(updated);
        conversation.setVersion(conversation.getVersion() + 1);
        conversation.setUpdatedAt(now);
        saveSystemMessage(
                conversation,
                "管理员“" + sourceAdmin.getDisplayName()
                        + "”已将会话转交给“"
                        + targetAdmin.getDisplayName() + "”："
                        + transferDTO.reason(),
                now
        );
        return toConversationVO(conversation);
    }

    /** 管理员可以按会话类型和状态查看系统中的全部会话。 */
    @Override
    @Transactional(readOnly = true)
    public PageVO<ChatConversationListVO> listAdminConversations(
            ChatConversationPageQueryDTO queryDTO
    ) {
        Page<ChatConversationListVO> page = new Page<>(
                queryDTO.page(), queryDTO.pageSize()
        );
        IPage<ChatConversationListVO> result =
                conversationMapper.selectAdminConversationPage(
                        page,
                        enumName(queryDTO.type()),
                        enumName(queryDTO.status())
                );
        return toPageVO(result);
    }

    private MeterReadingTask requireReadingTask(Long taskId) {
        requirePositiveId(taskId, "抄表任务ID必须大于0");
        MeterReadingTask task = readingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("抄表任务不存在");
        }
        return task;
    }

    private void requireManualTask(MeterReadingTask task) {
        if (!TaskExecutorType.METER_READER.name().equals(
                task.getExecutorType())
                || task.getMeterReaderId() == null) {
            throw new IllegalArgumentException("只有人工抄表任务可以创建人员沟通会话");
        }
    }

    private List<Long> selectResidentIds(Long meterId) {
        return residentMeterMapper.selectList(
                        Wrappers.<ResidentMeter>lambdaQuery()
                                .select(ResidentMeter::getResidentId)
                                .eq(ResidentMeter::getMeterId, meterId)
                ).stream()
                .map(ResidentMeter::getResidentId)
                .distinct()
                .toList();
    }

    private void requireTaskParticipant(
            Long currentUserId,
            MeterReadingTask task,
            List<Long> residentIds
    ) {
        boolean assignedReader = currentUserId.equals(
                task.getMeterReaderId()
        );
        boolean boundResident = residentIds.contains(currentUserId);
        if (!assignedReader && !boundResident) {
            throw new ResourceNotFoundException("抄表任务不存在或无权访问");
        }
        requireEnabledUserWithRole(
                currentUserId,
                assignedReader ? ROLE_METER_READER : ROLE_RESIDENT,
                assignedReader ? "抄表员" : "居民"
        );
    }

    private SysUser requireEnabledUserWithRole(
            Long userId,
            String roleCode,
            String userLabel
    ) {
        requirePositiveId(userId, userLabel + "ID必须大于0");
        SysUser user = sysUserService.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(userLabel + "不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException(userLabel + "账号已被禁用");
        }
        if (!sysUserService.findRoleCodesByUserId(userId)
                .contains(roleCode)) {
            throw new IllegalArgumentException(
                    "指定用户不具有" + roleCode + "角色"
            );
        }
        return user;
    }

    private ChatConversation requireConversationForUpdate(
            Long conversationId
    ) {
        requirePositiveId(conversationId, "会话ID必须大于0");
        ChatConversation conversation =
                conversationMapper.selectByIdForUpdate(conversationId);
        if (conversation == null) {
            throw new ResourceNotFoundException("聊天会话不存在");
        }
        return conversation;
    }

    private ChatConversationMember requireActiveMember(
            Long userId,
            Long conversationId
    ) {
        requirePositiveId(userId, "当前用户ID不合法");
        requirePositiveId(conversationId, "会话ID必须大于0");
        ChatConversationMember member = findActiveMember(
                conversationId,
                userId
        );
        if (member == null) {
            throw new ResourceNotFoundException("聊天会话不存在或无权访问");
        }
        return member;
    }

    private ChatConversationMember findActiveMember(
            Long conversationId,
            Long userId
    ) {
        return memberMapper.selectOne(
                Wrappers.<ChatConversationMember>lambdaQuery()
                        .eq(ChatConversationMember::getConversationId,
                                conversationId)
                        .eq(ChatConversationMember::getUserId, userId)
                        .isNull(ChatConversationMember::getLeftAt)
                        .last("LIMIT 1")
        );
    }

    private void saveMember(
            Long conversationId,
            Long userId,
            ChatMemberRole role,
            LocalDateTime joinedAt
    ) {
        ChatConversationMember existing = memberMapper.selectOne(
                Wrappers.<ChatConversationMember>lambdaQuery()
                        .eq(ChatConversationMember::getConversationId,
                                conversationId)
                        .eq(ChatConversationMember::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (existing == null) {
            memberMapper.insert(ChatConversationMember.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .memberRole(role.name())
                    .joinedAt(joinedAt)
                    .build());
            return;
        }
        if (existing.getLeftAt() == null) {
            return;
        }
        memberMapper.update(
                null,
                Wrappers.<ChatConversationMember>lambdaUpdate()
                        .set(ChatConversationMember::getMemberRole,
                                role.name())
                        .set(ChatConversationMember::getJoinedAt, joinedAt)
                        .set(ChatConversationMember::getLeftAt, null)
                        .set(ChatConversationMember::getLastReadMessageId,
                                null)
                        .set(ChatConversationMember::getLastReadAt, null)
                        .eq(ChatConversationMember::getConversationId,
                                conversationId)
                        .eq(ChatConversationMember::getUserId, userId)
        );
    }

    private void saveTextMessage(
            ChatConversation conversation,
            Long senderId,
            ChatMessageSenderRole senderRole,
            String clientMessageId,
            String content,
            LocalDateTime createdAt
    ) {
        ChatMessage message = ChatMessage.builder()
                .conversationId(conversation.getId())
                .clientMessageId(clientMessageId)
                .senderId(senderId)
                .senderRole(senderRole.name())
                .messageType(ChatMessageType.TEXT.name())
                .content(content)
                .deleted(0)
                .createdAt(createdAt)
                .build();
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException("消息已提交，请勿重复发送", exception);
        }
        updateLastMessage(conversation, message, createdAt);
    }

    private void saveSystemMessage(
            ChatConversation conversation,
            String content,
            LocalDateTime createdAt
    ) {
        ChatMessage message = ChatMessage.builder()
                .conversationId(conversation.getId())
                .clientMessageId("SYSTEM-" + UUID.randomUUID())
                .senderRole(ChatMessageSenderRole.SYSTEM.name())
                .messageType(ChatMessageType.SYSTEM.name())
                .content(content)
                .deleted(0)
                .createdAt(createdAt)
                .build();
        messageMapper.insert(message);
        updateLastMessage(conversation, message, createdAt);

        ChatMessageSenderRole senderRole =
                ChatMessageSenderRole.SYSTEM;
        ChatMessageType messageType = ChatMessageType.SYSTEM;
        ChatMessageVO messageVO = new ChatMessageVO(
                message.getId(),
                message.getConversationId(),
                message.getClientMessageId(),
                null,
                "系统",
                senderRole,
                senderRole.getDescription(),
                messageType,
                messageType.getDescription(),
                message.getContent(),
                false,
                message.getCreatedAt()
        );
        eventPublisher.publishEvent(new ChatMessageSentEvent(
                messageVO,
                selectActiveMemberIds(conversation.getId())
        ));
    }

    /** 查询当前仍在会话中的成员，用于事务提交后的实时消息推送。 */
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

    private void updateLastMessage(
            ChatConversation conversation,
            ChatMessage message,
            LocalDateTime updatedAt
    ) {
        int updated = conversationMapper.update(
                null,
                Wrappers.<ChatConversation>lambdaUpdate()
                        .set(ChatConversation::getLastMessageId,
                                message.getId())
                        .set(ChatConversation::getLastMessageAt,
                                message.getCreatedAt())
                        .set(ChatConversation::getUpdatedAt, updatedAt)
                        .eq(ChatConversation::getId, conversation.getId())
        );
        if (updated != 1) {
            throw new ResourceNotFoundException("聊天会话不存在");
        }
        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageAt(message.getCreatedAt());
        conversation.setUpdatedAt(updatedAt);
    }

    private void requireAdminSupportConversation(
            ChatConversation conversation
    ) {
        if (!ChatConversationType.ADMIN_SUPPORT.name().equals(
                conversation.getConversationType())) {
            throw new IllegalArgumentException("该会话不是管理员客服会话");
        }
    }

    private void requireStatus(
            ChatConversation conversation,
            ChatConversationStatus expected,
            String message
    ) {
        if (statusOf(conversation) != expected) {
            throw new IllegalArgumentException(message);
        }
    }

    private ChatConversationStatus statusOf(
            ChatConversation conversation
    ) {
        return ChatConversationStatus.valueOf(conversation.getStatus());
    }

    private void requireVersion(Integer requestVersion, Integer dbVersion) {
        if (requestVersion == null || requestVersion < 0) {
            throw new IllegalArgumentException("会话版本不能为空且不能小于0");
        }
        if (!requestVersion.equals(dbVersion)) {
            throw new VersionConflictException("会话已被其他操作修改，请刷新后重试");
        }
    }

    private void ensureConversationUpdated(int updatedRows) {
        if (updatedRows != 1) {
            throw new VersionConflictException("会话已被其他操作修改，请刷新后重试");
        }
    }

    private ChatConversationVO toConversationVO(
            ChatConversation conversation
    ) {
        ChatConversationType type = ChatConversationType.valueOf(
                conversation.getConversationType()
        );
        ChatConversationStatus status = statusOf(conversation);
        return new ChatConversationVO(
                conversation.getId(),
                type,
                type.getDescription(),
                conversation.getTaskId(),
                conversation.getSubject(),
                status,
                status.getDescription(),
                conversation.getVersion(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private PageVO<ChatConversationListVO> toPageVO(
            IPage<ChatConversationListVO> result
    ) {
        return new PageVO<>(
                result.getRecords(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
