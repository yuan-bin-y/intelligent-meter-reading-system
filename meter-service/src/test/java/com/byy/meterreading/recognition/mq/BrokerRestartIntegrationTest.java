package com.byy.meterreading.recognition.mq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 会重启本项目测试Broker，必须额外明确设置开关；绝不操作业务Broker。 */
@EnabledIfEnvironmentVariable(named = "METER_IT_BROKER_RESTART", matches = "true")
class BrokerRestartIntegrationTest {
    @Test
    void confirmedQuorumMessageSurvivesBrokerRestartAndUnackedRedelivery() throws Exception {
        var factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(15680);
        factory.setUsername("test");
        factory.setPassword("test-only-password");
        factory.setAutomaticRecoveryEnabled(false);
        String name = "meter.it.restart." + UUID.randomUUID();
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.queueDeclare(name, true, false, false, new RecognitionMqConfig().recognitionDeadLetterQueue().getArguments());
            channel.confirmSelect();
            channel.basicPublish("", name, new AMQP.BasicProperties.Builder().deliveryMode(2).messageId(name).build(),
                    name.getBytes(StandardCharsets.UTF_8));
            channel.waitForConfirmsOrDie(5000);
        }
        // 本机固定测试项目名，CI使用Actions传入的临时service容器ID。
        String container = System.getenv().getOrDefault("METER_IT_RABBIT_CONTAINER", "meter-hardening-tests-rabbitmq-1");
        if (!container.equals("meter-hardening-tests-rabbitmq-1") && !container.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("只接受固定测试容器或CI service容器ID");
        }
        var inspect = new ProcessBuilder("docker", "inspect", "--format", "{{json .NetworkSettings.Ports}}", container)
                .redirectErrorStream(true).start();
        String ports = new String(inspect.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, inspect.waitFor());
        assertTrue(ports.contains("15680"), "只能重启发布了隔离测试端口15680的Broker");
        var restart = new ProcessBuilder("docker", "restart", container)
                .redirectErrorStream(true).start();
        assertTrue(restart.waitFor(45, TimeUnit.SECONDS));
        assertEquals(0, restart.exitValue());
        com.rabbitmq.client.Connection recovered = null;
        for (int i = 0; i < 30 && recovered == null; i++) {
            try {
                recovered = factory.newConnection();
            } catch (java.io.IOException | java.util.concurrent.TimeoutException expected) {
                Thread.sleep(500);
            }
        }
        assertNotNull(recovered);
        try (var channel = recovered.createChannel()) {
            var message = channel.basicGet(name, false);
            assertNotNull(message);
            assertEquals(name, message.getProps().getMessageId());
            // 不ACK就关闭信道，模拟处理完成但ACK丢失。下一消费者必须看到同一个eventId。
        }
        try (var channel = recovered.createChannel()) {
            var redelivered = channel.basicGet(name, false);
            assertNotNull(redelivered);
            assertTrue(redelivered.getEnvelope().isRedeliver());
            assertEquals(name, redelivered.getProps().getMessageId());
            channel.basicAck(redelivered.getEnvelope().getDeliveryTag(), false);
            channel.queueDelete(name);
        } finally {
            recovered.close();
        }
    }
}
