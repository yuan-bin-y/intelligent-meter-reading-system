package com.byy.meterreading.service.impl;

import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.dto.airecognition.CompleteAiRecognitionTaskDTO;
import com.byy.meterreading.mapper.AiRecognitionTaskMapper;
import com.byy.meterreading.mapper.MeterImageMapper;
import com.byy.meterreading.mapper.MeterMapper;
import com.byy.meterreading.mapper.MeterReadingResultImageMapper;
import com.byy.meterreading.mapper.MeterReadingResultMapper;
import com.byy.meterreading.mapper.MeterReadingTaskMapper;
import com.byy.meterreading.mapper.MqOutboxEventMapper;
import com.byy.meterreading.model.AiRecognitionTask;
import com.byy.meterreading.model.MqOutboxEvent;
import com.byy.meterreading.service.BusinessNotificationService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiRecognitionIdempotencyTest {
    private final AiRecognitionTaskMapper tasks = mock(AiRecognitionTaskMapper.class);
    private final MqOutboxEventMapper outbox = mock(MqOutboxEventMapper.class);
    private final MeterReadingResultMapper results = mock(MeterReadingResultMapper.class);
    private final MeterReadingTaskMapper readingTasks = mock(MeterReadingTaskMapper.class);
    private final MeterImageMapper images = mock(MeterImageMapper.class);
    private final BusinessNotificationService notifications = mock(BusinessNotificationService.class);
    private final AiRecognitionTaskServiceImpl service = new AiRecognitionTaskServiceImpl(
            tasks, outbox, images, mock(MeterMapper.class), readingTasks, results,
            mock(MeterReadingResultImageMapper.class), new ObjectMapper(), notifications);

    @Test
    void duplicateSuccessCallbackDoesNotCreateAnotherResultOrNotification() {
        prepareTask("SUCCEEDED", 81L);
        service.completeTask(1L, callback("event-current", 1));
        service.completeTask(1L, callback("event-current", 1));
        verifyNoInteractions(results, readingTasks, images, notifications);
    }

    @Test
    void callbackFromOldEventCannotOverwriteCurrentAttempt() {
        prepareTask("PROCESSING", null);
        assertThrows(ResourceConflictException.class,
                () -> service.completeTask(1L, callback("event-old", 1)));
        verifyNoInteractions(results, readingTasks, images, notifications);
    }

    @Test
    void callbackFromWrongAttemptIsRejectedBeforeAnyWrites() {
        prepareTask("PROCESSING", null);
        assertThrows(ResourceConflictException.class,
                () -> service.completeTask(1L, callback("event-current", 2)));
        verifyNoInteractions(results, readingTasks, images, notifications);
    }

    private void prepareTask(String status, Long resultId) {
        when(tasks.selectByIdForUpdate(1L)).thenReturn(AiRecognitionTask.builder()
                .id(1L).attemptNo(1).status(status).resultId(resultId).build());
        when(outbox.selectLatestByAggregate("AI_RECOGNITION_TASK", 1L)).thenReturn(
                MqOutboxEvent.builder().eventId("event-current").status("SENT").build());
    }

    private CompleteAiRecognitionTaskDTO callback(String eventId, int attempt) {
        return new CompleteAiRecognitionTaskDTO(eventId, attempt, new BigDecimal("92.17"),
                new BigDecimal("0.95"), "ocr", "v1", "{}", 10L);
    }
}
