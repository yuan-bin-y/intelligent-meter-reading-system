package com.byy.meterreading.aisimulator.mq;

import com.byy.meterreading.aisimulator.callback.AiCallbackClient;
import com.byy.meterreading.aisimulator.config.AiSimulatorProperties;
import com.byy.meterreading.aisimulator.recognition.RecognitionEngine;
import com.byy.meterreading.aisimulator.recognition.RecognitionOutcome;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecognitionTaskConsumerTest {
    private final RecognitionEngine engine = mock(RecognitionEngine.class);
    private final AiCallbackClient callback = mock(AiCallbackClient.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final AiSimulatorProperties properties = mock(AiSimulatorProperties.class);
    private final Channel channel = mock(Channel.class);
    private final RecognitionTaskConsumer consumer = new RecognitionTaskConsumer(engine, callback, rabbit, properties);
    private final RecognitionTaskMessage task = new RecognitionTaskMessage("event", 1L, 2L, 3L, 4L,
            "test", "image.jpg", "WATER", "LCD", 1);

    @Test
    void finalDlqUnroutableMessageMustNotAckTheOriginal() throws Exception {
        when(properties.maxConsumeAttempts()).thenReturn(1);
        when(properties.publisherConfirmTimeout()).thenReturn(Duration.ofSeconds(1));
        when(engine.recognize(task)).thenThrow(new IllegalStateException("injected failure"));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(message(), 312, "NO_ROUTE", "dlx", "dead"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        consumer.consume(task, message(), channel);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel).basicReject(7, false);
    }

    @Test
    void successfulCallbackHappensBeforeAck() throws Exception {
        var result = new RecognitionOutcome.Success(BigDecimal.TEN, BigDecimal.ONE, "test", "1", "{}");
        when(engine.recognize(task)).thenReturn(result);
        consumer.consume(task, message(), channel);
        var order = inOrder(callback, channel);
        order.verify(callback).start(task);
        order.verify(callback).complete(any(), any(), anyLong());
        order.verify(channel).basicAck(7, false);
    }

    @Test
    void ackFailureAfterCallbackKeepsTheMessageOnTheRetryPath() throws Exception {
        when(properties.maxConsumeAttempts()).thenReturn(3);
        when(engine.recognize(task)).thenReturn(new RecognitionOutcome.Failure("BLUR", "blurred"));
        doThrow(new IOException("connection lost before ACK")).when(channel).basicAck(7, false);
        consumer.consume(task, message(), channel);
        verify(callback).fail(any(), any(), anyLong());
        verify(channel).basicReject(7, false);
    }

    private Message message() {
        var headers = new MessageProperties();
        headers.setDeliveryTag(7);
        return new Message(new byte[0], headers);
    }
}
