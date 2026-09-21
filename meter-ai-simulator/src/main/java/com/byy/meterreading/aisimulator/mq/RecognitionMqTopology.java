package com.byy.meterreading.aisimulator.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 与 Java 后端保持一致的 AI 识别 RabbitMQ 拓扑。
 *
 * <p>模拟器可以先于后端启动，因此这里同样声明交换机、队列和绑定。
 * RabbitMQ 会把完全相同的重复声明视为幂等操作。</p>
 */
@Configuration
public class RecognitionMqTopology {

    public static final String RECOGNITION_EXCHANGE =
            "meter.recognition.exchange";
    public static final String RECOGNITION_QUEUE =
            "meter.recognition.queue";
    public static final String RECOGNITION_ROUTING_KEY =
            "meter.recognition.task";

    public static final String RETRY_EXCHANGE =
            "meter.recognition.retry.exchange";
    public static final String RETRY_QUEUE =
            "meter.recognition.retry.queue";
    public static final String RETRY_ROUTING_KEY =
            "meter.recognition.retry";

    public static final String DEAD_LETTER_EXCHANGE =
            "meter.recognition.dlx";
    public static final String DEAD_LETTER_QUEUE =
            "meter.recognition.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY =
            "meter.recognition.dead";

    private static final int RETRY_DELAY_MILLIS = 30_000;

    /** 非 Web 模块显式提供 Jackson，用于 MQ 消息和回调请求序列化。 */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public DirectExchange recognitionExchange() {
        return new DirectExchange(RECOGNITION_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange recognitionRetryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange recognitionDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue recognitionQueue() {
        return QueueBuilder.durable(RECOGNITION_QUEUE)
                .deadLetterExchange(RETRY_EXCHANGE)
                .deadLetterRoutingKey(RETRY_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue recognitionRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(RETRY_DELAY_MILLIS)
                .deadLetterExchange(RECOGNITION_EXCHANGE)
                .deadLetterRoutingKey(RECOGNITION_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue recognitionDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding recognitionBinding(
            @Qualifier("recognitionQueue") Queue queue,
            @Qualifier("recognitionExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(RECOGNITION_ROUTING_KEY);
    }

    @Bean
    public Binding recognitionRetryBinding(
            @Qualifier("recognitionRetryQueue") Queue queue,
            @Qualifier("recognitionRetryExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding recognitionDeadLetterBinding(
            @Qualifier("recognitionDeadLetterQueue") Queue queue,
            @Qualifier("recognitionDeadLetterExchange") DirectExchange exchange
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter recognitionMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
