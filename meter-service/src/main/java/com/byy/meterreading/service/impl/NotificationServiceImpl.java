package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.dto.notification.NotificationPageQueryDTO;
import com.byy.meterreading.mapper.NotificationMapper;
import com.byy.meterreading.mapper.projection.NotificationRow;
import com.byy.meterreading.model.Notification;
import com.byy.meterreading.model.enums.NotificationResourceType;
import com.byy.meterreading.model.enums.NotificationType;
import com.byy.meterreading.service.NotificationService;
import com.byy.meterreading.service.command.CreateNotificationCommand;
import com.byy.meterreading.service.event.NotificationCreatedEvent;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.notification.NotificationUnreadCountVO;
import com.byy.meterreading.vo.notification.NotificationVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 通知中心业务实现，MySQL 是通知事实的最终数据源。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_DEDUP_KEY_LENGTH = 128;

    private final NotificationMapper notificationMapper;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationServiceImpl(
            NotificationMapper notificationMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notificationMapper = notificationMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<NotificationVO> list(
            Long userId,
            NotificationPageQueryDTO queryDTO
    ) {
        requirePositiveId(userId, "用户ID不合法");
        Page<NotificationRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<NotificationRow> result =
                notificationMapper.selectNotificationPage(
                        page,
                        userId,
                        queryDTO.unreadOnly()
                );
        return new PageVO<>(
                result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationUnreadCountVO getUnreadCount(Long userId) {
        requirePositiveId(userId, "用户ID不合法");
        return new NotificationUnreadCountVO(
                notificationMapper.countUnread(userId)
        );
    }

    @Override
    @Transactional
    public NotificationVO markRead(Long userId, Long notificationId) {
        requirePositiveId(userId, "用户ID不合法");
        requirePositiveId(notificationId, "通知ID必须大于0");
        NotificationRow current = requireView(notificationId, userId);
        if (!Integer.valueOf(1).equals(current.getIsRead())) {
            notificationMapper.markRead(notificationId, userId);
        }
        return toVO(requireView(notificationId, userId));
    }

    @Override
    @Transactional
    public NotificationUnreadCountVO markAllRead(Long userId) {
        requirePositiveId(userId, "用户ID不合法");
        notificationMapper.markAllRead(userId);
        return new NotificationUnreadCountVO(
                notificationMapper.countUnread(userId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationVO> replayAfter(
            Long userId,
            Long lastEventId,
            int limit
    ) {
        requirePositiveId(userId, "用户ID不合法");
        requirePositiveId(lastEventId, "Last-Event-ID必须大于0");
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("SSE补偿数量必须在1到500之间");
        }
        return notificationMapper.selectAfterId(userId, lastEventId, limit)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationVO getForDelivery(
            Long notificationId,
            Long recipientUserId
    ) {
        requirePositiveId(notificationId, "通知ID必须大于0");
        requirePositiveId(recipientUserId, "接收用户ID不合法");
        return toVO(requireView(notificationId, recipientUserId));
    }

    /**
     * 通知与调用方业务共用事务；提交成功后再发布实时投递事件。
     * dedupKey 唯一索引负责拦截定时任务或消息重试产生的重复通知。
     */
    @Override
    @Transactional
    public NotificationVO create(CreateNotificationCommand command) {
        validateCommand(command);
        LocalDateTime now = LocalDateTime.now();
        Notification notification = Notification.builder()
                .recipientUserId(command.recipientUserId())
                .actorUserId(command.actorUserId())
                .notificationType(command.notificationType().name())
                .resourceType(command.resourceType().name())
                .resourceId(command.resourceId())
                .title(shorten(command.title(), MAX_TITLE_LENGTH))
                .content(shorten(command.content(), MAX_CONTENT_LENGTH))
                .dedupKey(normalizeDedupKey(command.dedupKey()))
                .isRead(0)
                .createdAt(now)
                .build();
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException exception) {
            Notification existing = notificationMapper.selectByDedupKey(
                    notification.getDedupKey()
            );
            if (existing == null
                    || !command.recipientUserId().equals(
                    existing.getRecipientUserId())) {
                throw exception;
            }
            return toVO(requireView(
                    existing.getId(),
                    existing.getRecipientUserId()
            ));
        }

        eventPublisher.publishEvent(new NotificationCreatedEvent(
                notification.getId(),
                command.recipientUserId()
        ));
        return toVO(requireView(
                notification.getId(),
                command.recipientUserId()
        ));
    }

    private NotificationRow requireView(
            Long notificationId,
            Long recipientUserId
    ) {
        NotificationRow row = notificationMapper.selectViewForRecipient(
                notificationId,
                recipientUserId
        );
        if (row == null) {
            // 不区分“不存在”和“不属于本人”，避免泄露其他用户通知。
            throw new ResourceNotFoundException("通知不存在");
        }
        return row;
    }

    private NotificationVO toVO(NotificationRow row) {
        NotificationType type = NotificationType.valueOf(
                row.getNotificationType()
        );
        NotificationResourceType resourceType =
                NotificationResourceType.valueOf(row.getResourceType());
        return new NotificationVO(
                row.getNotificationId(),
                type,
                type.getDescription(),
                row.getActorUserId(),
                row.getActorDisplayName(),
                resourceType,
                resourceType.getDescription(),
                row.getResourceId(),
                row.getTitle(),
                row.getContent(),
                Integer.valueOf(1).equals(row.getIsRead()),
                row.getReadAt(),
                row.getCreatedAt()
        );
    }

    private void validateCommand(CreateNotificationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("通知创建参数不能为空");
        }
        requirePositiveId(command.recipientUserId(), "接收用户ID不合法");
        if (command.actorUserId() != null) {
            requirePositiveId(command.actorUserId(), "触发用户ID不合法");
        }
        if (command.notificationType() == null) {
            throw new IllegalArgumentException("通知类型不能为空");
        }
        if (command.resourceType() == null) {
            throw new IllegalArgumentException("通知资源类型不能为空");
        }
        if (command.resourceId() != null) {
            requirePositiveId(command.resourceId(), "通知资源ID不合法");
        }
        requireText(command.title(), "通知标题不能为空");
        requireText(command.content(), "通知内容不能为空");
    }

    private String normalizeDedupKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String result = value.trim();
        if (result.length() > MAX_DEDUP_KEY_LENGTH) {
            throw new IllegalArgumentException("通知幂等键长度不能超过128个字符");
        }
        return result;
    }

    private String shorten(String value, int maxLength) {
        String result = value.trim();
        return result.length() <= maxLength
                ? result
                : result.substring(0, maxLength);
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
