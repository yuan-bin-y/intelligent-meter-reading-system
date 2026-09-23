package com.byy.meterreading.aisimulator.recognition;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.byy.meterreading.aisimulator.config.AiSimulatorMode;
import com.byy.meterreading.aisimulator.config.AiSimulatorProperties;
import com.byy.meterreading.aisimulator.mq.RecognitionTaskMessage;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * 真实视觉模型客户端。
 *
 * <p>根据 RabbitMQ 消息中的 Bucket 和 ObjectKey 从阿里云 OSS 下载原图，
 * 以 multipart/form-data 调用 Python FastAPI 的 {@code /api/v1/recognize}，
 * 再把模型响应转换成现有的成功或失败回调模型。</p>
 */
@Component
public class ModelRecognitionClient {

    private static final String RECOGNIZE_PATH = "/api/v1/recognize";

    private final AiSimulatorProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI recognizeUri;
    private final OSS ossClient;

    public ModelRecognitionClient(
            AiSimulatorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.modelRequestTimeout())
                .build();
        this.recognizeUri = resolveRecognizeUri(properties.modelBaseUrl());
        this.ossClient = createOssClient(properties);
    }

    public RecognitionOutcome recognize(RecognitionTaskMessage task) {
        if (properties.mode() != AiSimulatorMode.MODEL) {
            throw new IllegalStateException("只有MODEL模式允许调用真实视觉模型");
        }

        byte[] image = downloadImage(task);
        String boundary = "----MeterRecognition"
                + UUID.randomUUID().toString().replace("-", "");
        byte[] requestBody = createMultipartBody(boundary, task, image);

        HttpRequest request = HttpRequest.newBuilder(recognizeUri)
                .timeout(properties.modelRequestTimeout())
                .header(
                        "Content-Type",
                        "multipart/form-data; boundary=" + boundary
                )
                .header("X-Task-Id", task.eventId())
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();

        HttpResponse<String> response = send(request);
        JsonNode result = parseResponse(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // 503、500 等服务异常交给 MQ 重试；不把临时故障误记成业务失败。
            throw new IllegalStateException(
                    "视觉模型调用失败：HTTP " + response.statusCode()
                            + "，响应=" + abbreviate(response.body(), 500)
            );
        }
        if (!result.path("success").asBoolean(false)) {
            return new RecognitionOutcome.Failure(
                    textOrDefault(result, "errorCode", "MODEL_REJECTED"),
                    abbreviate(
                            textOrDefault(result, "message", "视觉模型识别失败"),
                            500
                    )
            );
        }

        String reading = text(result, "reading");
        if (reading == null || reading.isBlank()) {
            return new RecognitionOutcome.Failure(
                    textOrDefault(result, "failStage", "MODEL_NO_READING"),
                    abbreviate(
                            textOrDefault(result, "message", "视觉模型未返回有效读数"),
                            500
                    )
            );
        }

        BigDecimal recognizedValue = parseReading(reading);
        BigDecimal confidence = parseConfidence(result.get("confidence"));
        String modelName = textOrDefault(
                result,
                "modelName",
                properties.modelName()
        );
        String modelVersion = textOrDefault(
                result,
                "modelVersion",
                properties.modelVersion()
        );
        return new RecognitionOutcome.Success(
                recognizedValue,
                confidence,
                abbreviate(modelName, 128),
                abbreviate(modelVersion, 64),
                response.body()
        );
    }

    private byte[] downloadImage(RecognitionTaskMessage task) {
        if (ossClient == null) {
            throw new IllegalStateException("MODEL模式未初始化OSS客户端");
        }
        try (OSSObject object = ossClient.getObject(
                task.bucketName(),
                task.objectKey()
        ); InputStream input = object.getObjectContent()) {
            long declaredLength = object.getObjectMetadata().getContentLength();
            if (declaredLength > properties.maxImageBytes()) {
                throw new IllegalArgumentException("待识别图片超过大小限制");
            }
            byte[] bytes = input.readNBytes(
                    Math.toIntExact(properties.maxImageBytes() + 1)
            );
            if (bytes.length == 0) {
                throw new IllegalArgumentException("OSS中的待识别图片为空");
            }
            if (bytes.length > properties.maxImageBytes()) {
                throw new IllegalArgumentException("待识别图片超过大小限制");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("读取OSS待识别图片失败", exception);
        }
    }

    private byte[] createMultipartBody(
            String boundary,
            RecognitionTaskMessage task,
            byte[] image
    ) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeTextPart(output, boundary, "meter_type", task.meterType());
            writeTextPart(
                    output,
                    boundary,
                    "dial_type",
                    toModelDialType(task.displayType())
            );
            writeFilePart(
                    output,
                    boundary,
                    fileName(task.objectKey()),
                    contentType(task.objectKey()),
                    image
            );
            write(output, "--" + boundary + "--\r\n");
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("组装视觉模型请求失败", exception);
        }
    }

    private void writeTextPart(
            ByteArrayOutputStream output,
            String boundary,
            String name,
            String value
    ) throws IOException {
        if (value == null || value.isBlank()) {
            return;
        }
        write(output, "--" + boundary + "\r\n");
        write(
                output,
                "Content-Disposition: form-data; name=\"" + name
                        + "\"\r\n\r\n"
        );
        write(output, value + "\r\n");
    }

    private void writeFilePart(
            ByteArrayOutputStream output,
            String boundary,
            String fileName,
            String contentType,
            byte[] image
    ) throws IOException {
        write(output, "--" + boundary + "\r\n");
        write(
                output,
                "Content-Disposition: form-data; name=\"image\"; filename=\""
                        + fileName + "\"\r\n"
        );
        write(output, "Content-Type: " + contentType + "\r\n\r\n");
        output.write(image);
        write(output, "\r\n");
    }

    private void write(ByteArrayOutputStream output, String value)
            throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("视觉模型请求被中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法连接视觉模型服务", exception);
        }
    }

    private JsonNode parseResponse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new IllegalStateException("视觉模型返回了非法JSON", exception);
        }
    }

    private BigDecimal parseReading(String reading) {
        final BigDecimal value;
        try {
            value = new BigDecimal(reading.trim()).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "视觉模型返回的读数不是合法数字：" + reading,
                    exception
            );
        }
        if (value.signum() < 0 || value.precision() > 18
                || Math.max(value.scale(), 0) > 3) {
            throw new IllegalArgumentException(
                    "视觉模型返回的读数超出系统支持范围：" + reading
            );
        }
        return value;
    }

    private BigDecimal parseConfidence(JsonNode node) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException("视觉模型未返回合法置信度");
        }
        BigDecimal confidence = node.decimalValue()
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("视觉模型置信度必须在0到1之间");
        }
        return confidence;
    }

    private String toModelDialType(String displayType) {
        if ("LCD".equalsIgnoreCase(displayType)) {
            return "DIGITAL";
        }
        if ("MECHANICAL_ROLLER".equalsIgnoreCase(displayType)) {
            return "MECHANICAL";
        }
        return null;
    }

    private String contentType(String objectKey) {
        String lower = objectKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String fileName(String objectKey) {
        String normalized = objectKey.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String value = separator < 0
                ? normalized : normalized.substring(separator + 1);
        return value.isBlank() ? "meter-image.jpg" : value.replace("\"", "");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String textOrDefault(
            JsonNode node,
            String field,
            String defaultValue
    ) {
        String value = text(node, field);
        return value == null ? defaultValue : value;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private URI resolveRecognizeUri(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return URI.create("http://127.0.0.1:8001" + RECOGNIZE_PATH);
        }
        return URI.create(baseUrl.trim()).resolve(RECOGNIZE_PATH);
    }

    private OSS createOssClient(AiSimulatorProperties configuration) {
        if (configuration.mode() != AiSimulatorMode.MODEL) {
            return null;
        }
        return new OSSClientBuilder().build(
                configuration.ossEndpoint(),
                configuration.ossAccessKeyId(),
                configuration.ossAccessKeySecret()
        );
    }

    @PreDestroy
    void close() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}
