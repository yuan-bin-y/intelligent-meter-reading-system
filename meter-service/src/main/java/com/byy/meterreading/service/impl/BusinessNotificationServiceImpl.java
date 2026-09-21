package com.byy.meterreading.service.impl;

import com.byy.meterreading.mapper.NotificationRecipientMapper;
import com.byy.meterreading.model.Device;
import com.byy.meterreading.model.enums.NotificationResourceType;
import com.byy.meterreading.model.enums.NotificationType;
import com.byy.meterreading.service.BusinessNotificationService;
import com.byy.meterreading.service.NotificationService;
import com.byy.meterreading.service.command.CreateNotificationCommand;
import com.byy.meterreading.vo.chat.ChatMessageVO;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 将业务事件转换成统一通知，并集中处理接收人和幂等键。 */
@Service
public class BusinessNotificationServiceImpl
        implements BusinessNotificationService {

    private static final List<String> MANAGEMENT_ROLES =
            List.of("ADMIN", "AUDITOR");

    private final NotificationService notificationService;
    private final NotificationRecipientMapper recipientMapper;

    public BusinessNotificationServiceImpl(
            NotificationService notificationService,
            NotificationRecipientMapper recipientMapper
    ) {
        this.notificationService = notificationService;
        this.recipientMapper = recipientMapper;
    }

    @Override
    public void notifyDeviceOffline(Device device, Long alarmId) {
        for (Long userId : managementUsers()) {
            create(
                    userId,
                    null,
                    NotificationType.DEVICE_OFFLINE,
                    NotificationResourceType.DEVICE,
                    device.getId(),
                    "设备离线",
                    "设备“" + device.getDeviceName() + "”已离线",
                    "DEVICE_OFFLINE:" + alarmId + ":" + userId
            );
        }
    }

    @Override
    public void notifyDeviceRecovered(Device device, Long alarmId) {
        for (Long userId : managementUsers()) {
            create(
                    userId,
                    null,
                    NotificationType.DEVICE_RECOVERED,
                    NotificationResourceType.DEVICE,
                    device.getId(),
                    "设备恢复在线",
                    "设备“" + device.getDeviceName() + "”已恢复心跳",
                    "DEVICE_RECOVERED:" + alarmId + ":" + userId
            );
        }
    }

    @Override
    public void notifyAiRecognitionResult(
            Long recognitionTaskId,
            Long readingTaskId,
            boolean succeeded,
            String detail
    ) {
        NotificationType type = succeeded
                ? NotificationType.AI_RECOGNITION_SUCCEEDED
                : NotificationType.AI_RECOGNITION_FAILED;
        String title = succeeded ? "AI识别完成" : "AI识别失败";
        for (Long userId : taskAndManagementUsers(readingTaskId)) {
            create(
                    userId,
                    null,
                    type,
                    NotificationResourceType.AI_RECOGNITION_TASK,
                    recognitionTaskId,
                    title,
                    detail,
                    type.name() + ":" + recognitionTaskId + ":" + userId
            );
        }
    }

    @Override
    public void notifyTaskAssigned(
            Long taskId,
            Long actorUserId,
            Integer version
    ) {
        for (Long userId : taskParticipants(taskId)) {
            if (userId.equals(actorUserId)) {
                continue;
            }
            create(
                    userId,
                    actorUserId,
                    NotificationType.TASK_ASSIGNED,
                    NotificationResourceType.METER_READING_TASK,
                    taskId,
                    "抄表任务已分配",
                    "你有一项新的抄表任务或任务分配发生变化",
                    "TASK_ASSIGNED:" + taskId + ":" + version + ":" + userId
            );
        }
    }

    @Override
    public void notifyTaskStatusChanged(
            Long taskId,
            Long actorUserId,
            String statusName,
            Integer version
    ) {
        Set<Long> recipients = "待审核".equals(statusName)
                ? taskAndManagementUsers(taskId)
                : taskParticipants(taskId);
        for (Long userId : recipients) {
            if (userId.equals(actorUserId)) {
                continue;
            }
            create(
                    userId,
                    actorUserId,
                    NotificationType.TASK_STATUS_CHANGED,
                    NotificationResourceType.METER_READING_TASK,
                    taskId,
                    "抄表任务状态变化",
                    "任务状态已更新为：" + statusName,
                    "TASK_STATUS:" + taskId + ":" + version + ":" + userId
            );
        }
    }

    @Override
    public void notifyReviewDecision(
            Long resultId,
            Long taskId,
            Long reviewerId,
            boolean approved,
            String detail
    ) {
        NotificationType type = approved
                ? NotificationType.REVIEW_APPROVED
                : NotificationType.REVIEW_REJECTED;
        String title = approved ? "抄表结果审核通过" : "抄表结果审核驳回";
        for (Long userId : taskParticipants(taskId)) {
            if (userId.equals(reviewerId)) {
                continue;
            }
            create(
                    userId,
                    reviewerId,
                    type,
                    NotificationResourceType.METER_READING_RESULT,
                    resultId,
                    title,
                    detail,
                    type.name() + ":" + resultId + ":" + userId
            );
        }
    }

    @Override
    public void notifyChatMessage(
            ChatMessageVO message,
            List<Long> recipientUserIds
    ) {
        if (message == null || message.senderId() == null) {
            return;
        }
        for (Long userId : new LinkedHashSet<>(recipientUserIds)) {
            if (userId == null || userId.equals(message.senderId())) {
                continue;
            }
            create(
                    userId,
                    message.senderId(),
                    NotificationType.CHAT_MESSAGE,
                    NotificationResourceType.CHAT_CONVERSATION,
                    message.conversationId(),
                    "新的聊天消息",
                    message.senderDisplayName() + "：" + message.content(),
                    "CHAT_MESSAGE:" + message.messageId() + ":" + userId
            );
        }
    }

    private Set<Long> managementUsers() {
        return new LinkedHashSet<>(
                recipientMapper.selectEnabledUserIdsByRoleCodes(
                        MANAGEMENT_ROLES
                )
        );
    }

    private Set<Long> taskParticipants(Long taskId) {
        return new LinkedHashSet<>(
                recipientMapper.selectTaskParticipantUserIds(taskId)
        );
    }

    private Set<Long> taskAndManagementUsers(Long taskId) {
        Set<Long> result = taskParticipants(taskId);
        result.addAll(managementUsers());
        return result;
    }

    private void create(
            Long recipientUserId,
            Long actorUserId,
            NotificationType type,
            NotificationResourceType resourceType,
            Long resourceId,
            String title,
            String content,
            String dedupKey
    ) {
        notificationService.create(new CreateNotificationCommand(
                recipientUserId,
                actorUserId,
                type,
                resourceType,
                resourceId,
                title,
                content,
                dedupKey
        ));
    }
}
