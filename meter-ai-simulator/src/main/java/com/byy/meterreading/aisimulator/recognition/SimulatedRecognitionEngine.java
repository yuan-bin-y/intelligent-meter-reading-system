package com.byy.meterreading.aisimulator.recognition;

import com.byy.meterreading.aisimulator.config.AiSimulatorMode;
import com.byy.meterreading.aisimulator.config.AiSimulatorProperties;
import com.byy.meterreading.aisimulator.mq.RecognitionTaskMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** 根据配置产生固定结果的本地模拟识别引擎。 */
@Component
public class SimulatedRecognitionEngine implements RecognitionEngine {

    private final AiSimulatorProperties properties;
    private final ObjectMapper objectMapper;

    public SimulatedRecognitionEngine(
            AiSimulatorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public RecognitionOutcome recognize(RecognitionTaskMessage task) {
        waitForConfiguredDelay();

        if (properties.mode() == AiSimulatorMode.TRANSIENT_FAILURE) {
            throw new IllegalStateException("模拟AI服务临时不可用");
        }
        if (properties.mode() == AiSimulatorMode.FAILURE) {
            return new RecognitionOutcome.Failure(
                    properties.failureCode(),
                    properties.failureMessage()
            );
        }

        return new RecognitionOutcome.Success(
                properties.recognizedValue(),
                properties.confidence(),
                createRawResult(task)
        );
    }

    private String createRawResult(RecognitionTaskMessage task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("simulated", true);
        result.put("eventId", task.eventId());
        result.put("imageId", task.imageId());
        result.put("bucketName", task.bucketName());
        result.put("objectKey", task.objectKey());
        result.put("recognizedValue", properties.recognizedValue());
        result.put("confidence", properties.confidence());
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("模拟识别结果序列化失败", exception);
        }
    }

    private void waitForConfiguredDelay() {
        try {
            Thread.sleep(properties.processingDelay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模拟识别线程被中断", exception);
        }
    }
}
