package com.byy.meterreading.recognition.mq;

import com.byy.meterreading.model.MqOutboxEvent;
import com.byy.meterreading.support.IntegrationInfrastructure;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@EnabledIfEnvironmentVariable(named = "METER_IT", matches = "true")
class RecognitionMqIntegrationTest {
    @Test
    void deadLetterSurvivesMissingTargetBindingThenReturnsViaRetryQueue() throws Exception {
        var factory = connectionFactory();
        String name = "meter.it." + UUID.randomUUID();
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            String main = name + ".main";
            String retry = name + ".retry";
            String exchange = name + ".exchange";
            String retryExchange = name + ".retry.exchange";
            var config = new RecognitionMqConfig();
            var mainArgs = new HashMap<>(config.recognitionQueue().getArguments());
            assertEquals("quorum", mainArgs.get("x-queue-type"));
            assertEquals("at-least-once", mainArgs.get("x-dead-letter-strategy"));
            mainArgs.put("x-dead-letter-exchange", retryExchange);
            mainArgs.put("x-dead-letter-routing-key", "retry");
            var retryArgs = new HashMap<>(config.recognitionRetryQueue().getArguments());
            retryArgs.put("x-dead-letter-exchange", exchange);
            retryArgs.put("x-dead-letter-routing-key", "main");
            retryArgs.put("x-message-ttl", 200); // 只缩短隔离测试的等待时间。
            channel.exchangeDeclare(exchange, "direct", true);
            channel.exchangeDeclare(retryExchange, "direct", true);
            channel.queueDeclare(main, true, false, false, mainArgs);
            channel.queueDeclare(retry, true, false, false, retryArgs);
            channel.queueBind(main, exchange, "main");
            try {
                channel.confirmSelect();
                channel.basicPublish(exchange, "main", new AMQP.BasicProperties.Builder().deliveryMode(2).build(),
                        name.getBytes(StandardCharsets.UTF_8));
                channel.waitForConfirmsOrDie(5000);
                var first = awaitMessage(channel, main);
                channel.basicReject(first.getEnvelope().getDeliveryTag(), false);
                Thread.sleep(500); // 此时retry交换机没有绑定，默认classic死信可能直接丢弃。
                channel.queueBind(retry, retryExchange, "retry");
                var recovered = awaitMessage(channel, main);
                assertEquals(name, new String(recovered.getBody(), StandardCharsets.UTF_8));
                assertNotNull(recovered.getProps().getHeaders().get("x-death"));
                channel.basicAck(recovered.getEnvelope().getDeliveryTag(), false);
            } finally {
                channel.queueDelete(main);
                channel.queueDelete(retry);
                channel.exchangeDelete(exchange);
                channel.exchangeDelete(retryExchange);
            }
        }
    }

    @Test
    void confirmedPublishWithFailedDatabaseMarkIsRepublishedUsingTheSameEventId() throws Exception {
        var infra = new IntegrationInfrastructure();
        var rabbit = new CachingConnectionFactory(connectionFactory());
        rabbit.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        rabbit.setPublisherReturns(true);
        var template = new RabbitTemplate(rabbit);
        template.setMandatory(true);
        template.setMessageConverter(new JacksonJsonMessageConverter());
        String eventId = UUID.randomUUID().toString();
        String exchange = "meter.it." + eventId;
        String queue = exchange + ".queue";
        try (var connection = connectionFactory().newConnection(); var channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, "direct", true);
            channel.queueDeclare(queue, true, false, false, new RecognitionMqConfig().recognitionDeadLetterQueue().getArguments());
            channel.queueBind(queue, exchange, "task");
            var json = new ObjectMapper();
            var task = new RecognitionTaskMessage(eventId, 1L, 2L, 3L, 4L, "test", "image.jpg", "WATER", "LCD", 1);
            var event = MqOutboxEvent.builder().eventId(eventId).traceId(eventId.replace("-", ""))
                    .aggregateId(1L).aggregateType("AI_RECOGNITION_TASK")
                    .eventType("MQ_INTEGRATION").exchangeName(exchange).routingKey("task")
                    .payload(json.writeValueAsString(task)).status("PENDING").retryCount(0).maxRetryCount(5)
                    .nextRetryAt(LocalDateTime.now().minusSeconds(1)).version(0).build();
            infra.outbox.insert(event);
            var failingMark = spy(infra.outbox);
            doReturn(0).when(failingMark).markSent(anyLong(), anyInt(), any());
            var publisher = new RecognitionTaskPublisher(template, json, Duration.ofSeconds(5));
            var job = new OutboxEventPublisherJob(failingMark, publisher, 50, Duration.ofSeconds(1),
                    Duration.ofMillis(10), Duration.ofSeconds(1), "integration");
            job.publishPendingEvents();
            var first = awaitMessage(channel, queue);
            assertEquals("SENDING", infra.outbox.selectById(event.getId()).getStatus());
            infra.jdbc.update("UPDATE mq_outbox_event SET locked_at = NOW() - INTERVAL 5 SECOND WHERE id = ?", event.getId());
            var recoveredJob = new OutboxEventPublisherJob(infra.outbox, publisher, 50, Duration.ofSeconds(1),
                    Duration.ofMillis(10), Duration.ofSeconds(1), "integration");
            recoveredJob.publishPendingEvents();
            // DATETIME按秒保存，恢复时next_retry_at可能向上舍入，等下一轮调度再发送。
            Thread.sleep(1100);
            recoveredJob.publishPendingEvents();
            var duplicate = awaitMessage(channel, queue);
            assertEquals(eventId, first.getProps().getMessageId());
            assertEquals(eventId, duplicate.getProps().getMessageId());
            assertEquals("SENT", infra.outbox.selectById(event.getId()).getStatus());
            // 重新投递必须保留eventId；业务回调去重另外由AiRecognitionIdempotencyTest覆盖。
            channel.basicAck(duplicate.getEnvelope().getDeliveryTag(), false);
            channel.basicNack(first.getEnvelope().getDeliveryTag(), false, true);
            var redelivered = awaitMessage(channel, queue);
            assertTrue(redelivered.getEnvelope().isRedeliver());
            assertEquals(eventId, redelivered.getProps().getMessageId());
            channel.basicAck(redelivered.getEnvelope().getDeliveryTag(), false);
            channel.queueDelete(queue);
            channel.exchangeDelete(exchange);
            infra.jdbc.update("DELETE FROM mq_outbox_event WHERE event_id = ?", eventId);
        } finally {
            rabbit.destroy();
            infra.close();
        }
    }

    private static ConnectionFactory connectionFactory() {
        var factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(Integer.parseInt(System.getenv().getOrDefault("METER_IT_RABBIT_PORT", "15680")));
        factory.setUsername("test");
        factory.setPassword("test-only-password");
        return factory;
    }

    private static GetResponse awaitMessage(com.rabbitmq.client.Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline) {
            GetResponse message = channel.basicGet(queue, false);
            if (message != null) {
                return message;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等待消息超时：" + queue);
    }
}
