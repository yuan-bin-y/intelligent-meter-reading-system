package com.byy.meterreading.service;

import com.byy.meterreading.model.Device;
import com.byy.meterreading.vo.chat.ChatMessageVO;

import java.util.List;

/** 各业务模块使用的语义化通知入口。 */
public interface BusinessNotificationService {

    void notifyDeviceOffline(Device device, Long alarmId);

    void notifyDeviceRecovered(Device device, Long alarmId);

    void notifyAiRecognitionResult(
            Long recognitionTaskId,
            Long readingTaskId,
            boolean succeeded,
            String detail
    );

    void notifyTaskAssigned(
            Long taskId,
            Long actorUserId,
            Integer version
    );

    void notifyTaskStatusChanged(
            Long taskId,
            Long actorUserId,
            String statusName,
            Integer version
    );

    void notifyReviewDecision(
            Long resultId,
            Long taskId,
            Long reviewerId,
            boolean approved,
            String detail
    );

    void notifyChatMessage(
            ChatMessageVO message,
            List<Long> recipientUserIds
    );
}
