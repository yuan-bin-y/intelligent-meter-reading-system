package com.byy.meterreading.aisimulator.mq;

import com.byy.meterreading.aisimulator.callback.AiCallbackClient;
import com.byy.meterreading.aisimulator.config.AiSimulatorProperties;
import com.byy.meterreading.aisimulator.recognition.RecognitionEngine;
import com.byy.meterreading.aisimulator.recognition.RecognitionOutcome;
import com.byy.meterreading.common.trace.TraceIdContext;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI 识别任务消费者。
 *
 * <p>正常识别或确定的业务失败在后端回调成功后 ACK。网络错误、签名错误、
 * 后端暂不可用等异常拒绝消息，使其进入重试队列；达到最大消费次数后，
 * 将消息可靠发布到 DLQ 并 ACK 原消息。</p>
 */
@Component
public class RecognitionTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(
            RecognitionTaskConsumer.class
    );

    private final RecognitionEngine recognitionEngine;
    private final AiCallbackClient callbackClient;
    private final RabbitTemplate rabbitTemplate;
    private final AiSimulatorProperties properties;

    public RecognitionTaskConsumer(
            RecognitionEngine recognitionEngine,
            AiCallbackClient callbackClient,
            RabbitTemplate rabbitTemplate,
            AiSimulatorProperties properties
    ) {
        this.recognitionEngine = recognitionEngine;
        this.callbackClient = callbackClient;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @RabbitListener(
            queues = RecognitionMqTopology.RECOGNITION_QUEUE,
            ackMode = "MANUAL"
    )
    public void consume(
            RecognitionTaskMessage task,
            Message originalMessage,
            Channel channel
    ) throws IOException {
        Object traceIdHeader = originalMessage.getMessageProperties()
                .getHeaders().get(TraceIdContext.MESSAGE_HEADER);
        try (TraceIdContext.Scope ignored = TraceIdContext.open(
                traceIdHeader == null ? null : traceIdHeader.toString()
        )) {
            long deliveryTag = originalMessage.getMessageProperties()
                    .getDeliveryTag();
            int currentAttempt = currentAttempt(originalMessage);
            long startedNanos = System.nanoTime();

            try {
                validateTask(task);
                callbackClient.start(task);
                RecognitionOutcome outcome = recognitionEngine.recognize(task);
                long durationMs = elapsedMillis(startedNanos);

                if (outcome instanceof RecognitionOutcome.Success success) {
                    callbackClient.complete(task, success, durationMs);
                    log.info(
                            "模拟AI识别成功并完成回调：eventId={}, taskId={}, value={}",
                            task.eventId(),
                            task.recognitionTaskId(),
                            success.recognizedValue()
                    );
                } else if (outcome instanceof RecognitionOutcome.Failure failure) {
                    callbackClient.fail(task, failure, durationMs);
                    log.info(
                            "模拟AI识别失败并完成回调：eventId={}, taskId={}, code={}",
                            task.eventId(),
                            task.recognitionTaskId(),
                            failure.failureCode()
                    );
                } else {
                    throw new IllegalStateException("识别引擎返回了未知结果");
                }

                channel.basicAck(deliveryTag, false);
            } catch (Exception exception) {
                handleProcessingFailure(
                        task,
                        originalMessage,
                        channel,
                        deliveryTag,
                        currentAttempt,
                        exception
                );
            }
        }
    }

    private void handleProcessingFailure(
            RecognitionTaskMessage task,
            Message originalMessage,
            Channel channel,
            long deliveryTag,
            int currentAttempt,
            Exception exception
    ) throws IOException {
        String eventId = task == null ? null : task.eventId();
        Long taskId = task == null ? null : task.recognitionTaskId();

        if (currentAttempt < properties.maxConsumeAttempts()) {
            log.warn(
                    "AI任务消费失败，消息进入延迟重试：eventId={}, taskId={}, attempt={}/{}, reason={}",
                    eventId,
                    taskId,
                    currentAttempt,
                    properties.maxConsumeAttempts(),
                    exception.getMessage()
            );
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            publishToDeadLetter(originalMessage, currentAttempt, exception);
            channel.basicAck(deliveryTag, false);
            log.error(
                    "AI任务达到最大消费次数，已进入DLQ：eventId={}, taskId={}, attempts={}",
                    eventId,
                    taskId,
                    currentAttempt,
                    exception
            );
        } catch (RuntimeException deadLetterException) {
            log.error(
                    "AI任务发布DLQ失败，消息重新进入延迟重试：eventId={}, taskId={}",
                    eventId,
                    taskId,
                    deadLetterException
            );
            channel.basicReject(deliveryTag, false);
        }
    }

    /** 发布到 DLQ 后等待 Broker Confirm，避免先 ACK 导致消息丢失。 */
    private void publishToDeadLetter(
            Message originalMessage,
            int currentAttempt,
            Exception cause
    ) {
        MessageProperties deadProperties = new MessageProperties();
        deadProperties.setContentType(
                originalMessage.getMessageProperties().getContentType()
        );
        deadProperties.setContentEncoding(
                originalMessage.getMessageProperties().getContentEncoding()
        );
        deadProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        deadProperties.setMessageId(
                originalMessage.getMessageProperties().getMessageId()
        );
        deadProperties.setCorrelationId(
                originalMessage.getMessageProperties().getCorrelationId()
        );
        deadProperties.setHeader("x-final-attempt", currentAttempt);
        deadProperties.setHeader(
                "x-final-error",
                abbreviate(cause.getMessage())
        );
        Object traceIdHeader = originalMessage.getMessageProperties()
                .getHeaders().get(TraceIdContext.MESSAGE_HEADER);
        if (traceIdHeader != null) {
            deadProperties.setHeader(
                    TraceIdContext.MESSAGE_HEADER,
                    traceIdHeader.toString()
            );
        }

        Message deadMessage = new Message(
                originalMessage.getBody(),
                deadProperties
        );
        CorrelationData correlationData = new CorrelationData(
                "dlq-" + UUID.randomUUID()
        );
        rabbitTemplate.send(
                RecognitionMqTopology.DEAD_LETTER_EXCHANGE,
                RecognitionMqTopology.DEAD_LETTER_ROUTING_KEY,
                deadMessage,
                correlationData
        );

        CorrelationData.Confirm confirm = awaitConfirm(correlationData);
        if (correlationData.getReturned() != null) {
            throw new IllegalStateException("AI任务消息无法路由到DLQ");
        }
        if (!confirm.ack()) {
            throw new IllegalStateException(
                    "RabbitMQ拒绝DLQ消息：" + confirm.reason()
            );
        }
    }

    private CorrelationData.Confirm awaitConfirm(
            CorrelationData correlationData
    ) {
        try {
            return correlationData.getFuture().get(
                    properties.publisherConfirmTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待DLQ发送确认时线程被中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("等待DLQ发送确认失败", exception);
        }
    }

    /** x-death 中主队列的次数等于之前失败的消费次数。 */
    private int currentAttempt(Message message) {
        Object header = message.getMessageProperties()
                .getHeaders().get("x-death");
        long previousFailures = 0;
        if (header instanceof List<?> deaths) {
            for (Object item : deaths) {
                if (!(item instanceof Map<?, ?> death)) {
                    continue;
                }
                if (!RecognitionMqTopology.RECOGNITION_QUEUE.equals(
                        String.valueOf(death.get("queue"))
                )) {
                    continue;
                }
                Object count = death.get("count");
                if (count instanceof Number number) {
                    previousFailures = Math.max(
                            previousFailures,
                            number.longValue()
                    );
                }
            }
        }
        return Math.toIntExact(Math.min(
                previousFailures + 1,
                Integer.MAX_VALUE
        ));
    }

    private void validateTask(RecognitionTaskMessage task) {
        if (task == null
                || isBlank(task.eventId())
                || task.recognitionTaskId() == null
                || task.recognitionTaskId() <= 0
                || task.readingTaskId() == null
                || task.readingTaskId() <= 0
                || task.imageId() == null
                || task.imageId() <= 0
                || task.meterId() == null
                || task.meterId() <= 0
                || isBlank(task.bucketName())
                || isBlank(task.objectKey())
                || task.attemptNo() == null
                || task.attemptNo() <= 0) {
            throw new IllegalArgumentException("RabbitMQ中的AI识别任务消息不完整");
        }
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(
                0,
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startedNanos
                )
        );
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "未知错误";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
