package com.byy.meterreading.recognition.mq;

/**
 * Java 后端发送给 AI 视觉识别服务的 RabbitMQ 消息。
 *
 * @param eventId          Outbox 事件唯一编号，AI 回调时必须原样返回
 * @param recognitionTaskId AI 识别任务主键
 * @param readingTaskId    抄表任务主键
 * @param imageId          待识别图片主键
 * @param meterId          表具主键
 * @param bucketName       图片所在的 OSS Bucket
 * @param objectKey        图片在 OSS 中的对象路径
 * @param meterType        表具类型：WATER、ELECTRIC、GAS
 * @param displayType      表盘显示类型：LCD、MECHANICAL_ROLLER
 * @param attemptNo        同一图片的识别任务尝试序号
 */
public record RecognitionTaskMessage(
        String eventId,
        Long recognitionTaskId,
        Long readingTaskId,
        Long imageId,
        Long meterId,
        String bucketName,
        String objectKey,
        String meterType,
        String displayType,
        Integer attemptNo
) {
}
