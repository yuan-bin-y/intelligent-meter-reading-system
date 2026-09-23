package com.byy.meterreading.aisimulator.callback;

import com.byy.meterreading.aisimulator.config.AiSimulatorProperties;
import com.byy.meterreading.aisimulator.mq.RecognitionTaskMessage;
import com.byy.meterreading.aisimulator.recognition.RecognitionOutcome;
import com.byy.meterreading.common.trace.TraceIdContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 使用 HMAC-SHA256 调用 Java 后端的 AI 内部回调接口。
 *
 * <p>签名原文顺序与后端完全一致：服务编号、HTTP 方法、请求路径、
 * 时间戳、随机数、请求体 SHA-256，每个字段之间使用换行符。</p>
 */
@Component
public class AiCallbackClient {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HTTP_METHOD = "POST";

    private final AiSimulatorProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI backendBaseUri;
    private final SecretKeySpec signingKey;

    public AiCallbackClient(
            AiSimulatorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.backendBaseUri = URI.create(properties.backendBaseUrl());
        this.signingKey = createSigningKey(properties.secretBase64());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.callbackTimeout())
                .build();
    }

    /** 通知后端：AI 消费者已经收到并开始处理任务。 */
    public void start(RecognitionTaskMessage task) {
        post(
                callbackPath(task.recognitionTaskId(), "start"),
                new StartCallback(
                        task.eventId(),
                        task.attemptNo(),
                        properties.modelName(),
                        properties.modelVersion()
                )
        );
    }

    /** 通知后端：识别成功，并携带读数、置信度和模型原始结果。 */
    public void complete(
            RecognitionTaskMessage task,
            RecognitionOutcome.Success outcome,
            long processingDurationMs
    ) {
        post(
                callbackPath(task.recognitionTaskId(), "result"),
                new CompleteCallback(
                        task.eventId(),
                        task.attemptNo(),
                        outcome.recognizedValue(),
                        outcome.confidence(),
                        outcome.modelName(),
                        outcome.modelVersion(),
                        outcome.rawResult(),
                        processingDurationMs
                )
        );
    }

    /** 通知后端：图片无法识别等确定的业务失败。 */
    public void fail(
            RecognitionTaskMessage task,
            RecognitionOutcome.Failure outcome,
            long processingDurationMs
    ) {
        post(
                callbackPath(task.recognitionTaskId(), "failure"),
                new FailureCallback(
                        task.eventId(),
                        task.attemptNo(),
                        outcome.failureCode(),
                        outcome.failureMessage(),
                        processingDurationMs
                )
        );
    }

    private void post(String requestPath, Object payload) {
        byte[] body = writeJson(payload);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String bodySha256 = sha256Hex(body);
        String signature = sign(String.join(
                "\n",
                properties.serviceId(),
                HTTP_METHOD,
                requestPath,
                timestamp,
                nonce,
                bodySha256
        ));

        HttpRequest request = HttpRequest.newBuilder(
                        backendBaseUri.resolve(requestPath)
                )
                .timeout(properties.callbackTimeout())
                .header("Content-Type", "application/json")
                .header("X-AI-Service-Id", properties.serviceId())
                .header("X-AI-Timestamp", timestamp)
                .header("X-AI-Nonce", nonce)
                .header("X-AI-Signature", signature)
                .header(
                        TraceIdContext.HTTP_HEADER,
                        TraceIdContext.getOrCreate()
                )
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        final HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI回调请求被中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法连接Java后端AI回调接口", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "AI回调失败：HTTP " + response.statusCode()
                            + "，响应=" + abbreviate(response.body())
            );
        }
    }

    private String callbackPath(Long recognitionTaskId, String operation) {
        if (recognitionTaskId == null || recognitionTaskId <= 0) {
            throw new IllegalArgumentException("AI识别任务ID不合法");
        }
        return "/api/v1/internal/ai/recognition-tasks/"
                + recognitionTaskId + "/" + operation;
    }

    private byte[] writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("AI回调参数序列化失败", exception);
        }
    }

    private SecretKeySpec createSigningKey(String secretBase64) {
        final byte[] secret;
        try {
            secret = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "AI_SERVICE_SECRET_BASE64必须是合法Base64",
                    exception
            );
        }
        if (secret.length < 32) {
            throw new IllegalArgumentException(
                    "AI_SERVICE_SECRET_BASE64解码后不能少于32字节"
            );
        }
        return new SecretKeySpec(secret, HMAC_ALGORITHM);
    }

    private String sign(String canonicalRequest) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return HexFormat.of().formatHex(
                    mac.doFinal(canonicalRequest.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AI回调签名生成失败", exception);
        }
    }

    private String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500) + "...";
    }

    private record StartCallback(
            String eventId,
            int attemptNo,
            String modelName,
            String modelVersion
    ) {
    }

    private record CompleteCallback(
            String eventId,
            int attemptNo,
            BigDecimal recognizedValue,
            BigDecimal confidence,
            String modelName,
            String modelVersion,
            String rawResult,
            long processingDurationMs
    ) {
    }

    private record FailureCallback(
            String eventId,
            int attemptNo,
            String failureCode,
            String failureMessage,
            long processingDurationMs
    ) {
    }
}
