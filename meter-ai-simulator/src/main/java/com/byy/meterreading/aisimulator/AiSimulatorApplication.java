package com.byy.meterreading.aisimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI 视觉识别任务 Worker。
 *
 * <p>该程序独立消费 RabbitMQ 识别任务。MODEL 模式调用真实视觉模型，
 * 其余模式用于验证成功、失败、重试和死信流程。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSimulatorApplication.class, args);
    }
}
