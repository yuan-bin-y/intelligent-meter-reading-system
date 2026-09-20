package com.byy.meterreading.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建可复用的阿里云 OSS 客户端；应用关闭时由 Spring 调用 shutdown。
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(
            prefix = "app.storage.oss",
            name = "enabled",
            havingValue = "true"
    )
    public OSS ossClient(OssProperties properties) {
        properties.validateEnabledConfiguration();
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );
    }
}
