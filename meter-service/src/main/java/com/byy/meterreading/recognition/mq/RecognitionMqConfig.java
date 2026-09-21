package com.byy.meterreading.recognition.mq;

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

/**
 * AI 识别任务 RabbitMQ 拓扑配置。
 *
 * <p>主队列中的消息处理失败并被拒绝后进入重试队列；重试队列中的消息
 * 等待固定 TTL 后自动回到主交换机。AI 消费者判断超过最大消费次数时，
 * 应将原消息发布到死信交换机并 ACK 原消息，避免无限循环。</p>
 */
@Configuration
public class RecognitionMqConfig {

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

    /** 消费失败进入重试队列后，30秒再返回主识别队列。 */
    private static final int RETRY_DELAY_MILLIS = 30_000;

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

    /**
     * AI 主队列拒绝且不重新入队的消息，先进入重试交换机。
     */
    @Bean
    public Queue recognitionQueue() {
        return QueueBuilder.durable(RECOGNITION_QUEUE)
                .deadLetterExchange(RETRY_EXCHANGE)
                .deadLetterRoutingKey(RETRY_ROUTING_KEY)
                .build();
    }

    /**
     * 重试队列不设置消费者，消息等待 TTL 后经死信机制回到主交换机。
     */
    @Bean
    public Queue recognitionRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(RETRY_DELAY_MILLIS)
                .deadLetterExchange(RECOGNITION_EXCHANGE)
                .deadLetterRoutingKey(RECOGNITION_ROUTING_KEY)
                .build();
    }

    /** 最终失败消息由人工排查或后续补偿程序处理。 */
    @Bean
    public Queue recognitionDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding recognitionBinding(
            @Qualifier("recognitionQueue") Queue recognitionQueue,
            @Qualifier("recognitionExchange")
            DirectExchange recognitionExchange
    ) {
        return BindingBuilder.bind(recognitionQueue)
                .to(recognitionExchange)
                .with(RECOGNITION_ROUTING_KEY);
    }

    @Bean
    public Binding recognitionRetryBinding(
            @Qualifier("recognitionRetryQueue") Queue recognitionRetryQueue,
            @Qualifier("recognitionRetryExchange")
            DirectExchange recognitionRetryExchange
    ) {
        return BindingBuilder.bind(recognitionRetryQueue)
                .to(recognitionRetryExchange)
                .with(RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding recognitionDeadLetterBinding(
            @Qualifier("recognitionDeadLetterQueue")
            Queue recognitionDeadLetterQueue,
            @Qualifier("recognitionDeadLetterExchange")
            DirectExchange recognitionDeadLetterExchange
    ) {
        return BindingBuilder.bind(recognitionDeadLetterQueue)
                .to(recognitionDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    /** 使用 Jackson 3 输出跨语言可读取的 application/json 消息。 */
    @Bean
    public MessageConverter recognitionMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
