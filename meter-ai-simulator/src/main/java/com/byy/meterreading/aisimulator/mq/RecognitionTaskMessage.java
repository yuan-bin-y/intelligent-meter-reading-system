package com.byy.meterreading.aisimulator.mq;

/** Java 后端通过 RabbitMQ 发送给视觉识别服务的任务消息。 */
public record RecognitionTaskMessage(
        String eventId,
        Long recognitionTaskId,
        Long readingTaskId,
        Long imageId,
        Long meterId,
        String bucketName,
        String objectKey,
        Integer attemptNo
) {
}
