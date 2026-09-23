package com.byy.meterreading.aisimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** AI 识别任务 Worker。 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSimulatorApplication.class, args);
    }
}
