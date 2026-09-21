package com.byy.meterreading.aisimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI 视觉识别服务本地模拟器。
 *
 * <p>该程序独立消费 RabbitMQ 识别任务，并按真实 AI 服务的协议回调
 * Java 后端。以后接入真正的视觉模型时，只需要替换识别引擎。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSimulatorApplication.class, args);
    }
}
