package com.byy.meterreading.recognition.mq;

import com.byy.meterreading.common.trace.TraceIdContext;
import com.byy.meterreading.model.MqOutboxEvent;
import com.byy.meterreading.model.enums.MqOutboxStatus;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 将已经被 Outbox 发布任务抢占的 AI 识别消息发送到 RabbitMQ。
 *
 * <p>该类只负责消息转换、发布和等待 Broker Confirm，不更新
 * mq_outbox_event；Outbox 状态由调用方根据本方法是否抛出异常处理。</p>
 */
@Component
public class RecognitionTaskPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Duration confirmTimeout;

    public RecognitionTaskPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${app.recognition.mq.publisher-confirm-timeout:PT5S}")
            Duration confirmTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.confirmTimeout = confirmTimeout;
    }

    /**
     * 发送一条 SENDING 状态的 Outbox 事件并同步等待 RabbitMQ 确认。
     *
     * @throws IllegalArgumentException 事件内容或状态不合法
     * @throws IllegalStateException    消息转换、路由或 Broker Confirm 失败
     */
    public void publish(MqOutboxEvent event) {
        validateEvent(event);
        RecognitionTaskMessage message = readMessage(event.getPayload());
        validateMessageMatchesEvent(event, message);

        CorrelationData correlationData = new CorrelationData(
                event.getEventId()
        );
        rabbitTemplate.convertAndSend(
                event.getExchangeName(),
                event.getRoutingKey(),
                message,
                amqpMessage -> {
                    amqpMessage.getMessageProperties().setMessageId(
                            event.getEventId()
                    );
                    amqpMessage.getMessageProperties().setCorrelationId(
                            event.getEventId()
                    );
                    amqpMessage.getMessageProperties().setDeliveryMode(
                            MessageDeliveryMode.PERSISTENT
                    );
                    amqpMessage.getMessageProperties().setHeader(
                            "x-event-id", event.getEventId()
                    );
                    amqpMessage.getMessageProperties().setHeader(
                            TraceIdContext.MESSAGE_HEADER,
                            event.getTraceId()
                    );
                    return amqpMessage;
                },
                correlationData
        );

        CorrelationData.Confirm confirm = awaitConfirm(correlationData);
        ReturnedMessage returned = correlationData.getReturned();
        if (returned != null) {
            throw new IllegalStateException(
                    "RabbitMQ消息无法路由到队列：replyCode="
                            + returned.getReplyCode()
                            + ", replyText=" + returned.getReplyText()
                            + ", exchange=" + returned.getExchange()
                            + ", routingKey=" + returned.getRoutingKey()
            );
        }
        if (!confirm.ack()) {
            throw new IllegalStateException(
                    "RabbitMQ拒绝识别任务消息："
                            + (confirm.reason() == null
                            ? "原因未知" : confirm.reason())
            );
        }
    }

    private CorrelationData.Confirm awaitConfirm(
            CorrelationData correlationData
    ) {
        try {
            return correlationData.getFuture().get(
                    confirmTimeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "等待RabbitMQ发送确认时线程被中断",
                    exception
            );
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "等待RabbitMQ发送确认超时",
                    exception
            );
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "等待RabbitMQ发送确认失败",
                    exception.getCause() == null
                            ? exception : exception.getCause()
            );
        }
    }

    private RecognitionTaskMessage readMessage(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    RecognitionTaskMessage.class
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Outbox中的AI识别消息不是合法JSON",
                    exception
            );
        }
    }

    private void validateEvent(MqOutboxEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Outbox事件不能为空");
        }
        if (!MqOutboxStatus.SENDING.name().equals(event.getStatus())) {
            throw new IllegalArgumentException(
                    "只有SENDING状态的Outbox事件允许发送"
            );
        }
        requireText(event.getEventId(), "Outbox事件编号不能为空");
        requireText(event.getExchangeName(), "RabbitMQ交换机不能为空");
        requireText(event.getRoutingKey(), "RabbitMQ路由键不能为空");
        requireText(event.getPayload(), "Outbox消息内容不能为空");
    }

    private void validateMessageMatchesEvent(
            MqOutboxEvent event,
            RecognitionTaskMessage message
    ) {
        if (message == null
                || !event.getEventId().equals(message.eventId())
                || !event.getAggregateId().equals(
                message.recognitionTaskId()
        )) {
            throw new IllegalStateException(
                    "Outbox事件与AI识别消息内容不一致"
            );
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
