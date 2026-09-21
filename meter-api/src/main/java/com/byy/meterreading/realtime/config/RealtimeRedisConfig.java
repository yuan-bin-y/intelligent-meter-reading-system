package com.byy.meterreading.realtime.config;

import com.byy.meterreading.realtime.redis.RealtimeRedisSubscriber;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Redis Pub/Sub 仅负责跨实例分发，通知事实始终保存在 MySQL。 */
@Configuration
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimeRedisConfig {

    @Bean
    public RedisMessageListenerContainer notificationRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            RealtimeRedisSubscriber subscriber,
            RealtimeProperties properties
    ) {
        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                subscriber,
                new ChannelTopic(properties.redisChannel())
        );
        return container;
    }
}
