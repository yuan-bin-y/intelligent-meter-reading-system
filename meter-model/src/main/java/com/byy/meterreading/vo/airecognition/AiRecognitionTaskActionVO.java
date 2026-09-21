package com.byy.meterreading.vo.airecognition;

import com.byy.meterreading.model.enums.AiRecognitionStatus;

/** 创建、重试或取消 AI 识别任务后的最新状态。 */
public record AiRecognitionTaskActionVO(
        Long recognitionTaskId,
        String recognitionNo,
        AiRecognitionStatus status,
        String statusName,
        Integer version
) {
}
