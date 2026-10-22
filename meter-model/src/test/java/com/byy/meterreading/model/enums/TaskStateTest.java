package com.byy.meterreading.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStateTest {
    @Test
    void manualReadingRequiresExecutionAndReviewBeforeCompletion() {
        assertFalse(MeterReadingTaskStatus.PENDING.canTransitionTo(MeterReadingTaskStatus.COMPLETED));
        assertTrue(MeterReadingTaskStatus.PENDING.canTransitionTo(MeterReadingTaskStatus.PROCESSING));
        assertTrue(MeterReadingTaskStatus.PROCESSING.canTransitionTo(MeterReadingTaskStatus.PENDING_REVIEW));
        assertTrue(MeterReadingTaskStatus.PENDING_REVIEW.canTransitionTo(MeterReadingTaskStatus.COMPLETED));
    }

    @Test
    void completedAndCancelledTasksCannotBeResurrected() {
        for (MeterReadingTaskStatus target : MeterReadingTaskStatus.values()) {
            assertFalse(MeterReadingTaskStatus.COMPLETED.canTransitionTo(target));
            assertFalse(MeterReadingTaskStatus.CANCELLED.canTransitionTo(target));
        }
        assertFalse(MeterReadingTaskStatus.PROCESSING.canTransitionTo(null));
    }

    @Test
    void onlyFailedRecognitionCanBeRetried() {
        assertTrue(AiRecognitionStatus.FAILED.canTransitionTo(AiRecognitionStatus.PENDING));
        assertFalse(AiRecognitionStatus.SUCCEEDED.canTransitionTo(AiRecognitionStatus.PENDING));
        assertFalse(AiRecognitionStatus.CANCELLED.canTransitionTo(AiRecognitionStatus.PENDING));
        assertFalse(AiRecognitionStatus.PENDING.canTransitionTo(AiRecognitionStatus.SUCCEEDED));
    }
}
