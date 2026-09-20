package com.byy.meterreading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * 阿里云 OSS 图片存储配置；密钥只能通过环境变量或外部配置注入。
 */
@ConfigurationProperties(prefix = "app.storage.oss")
public class OssProperties {

    private boolean enabled;
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String accessKeySecret;
    private Duration accessUrlTtl = Duration.ofMinutes(5);
    private DataSize maxFileSize = DataSize.ofMegabytes(10);
    private int maxImagesPerTask = 5;
    private Duration cleanupRetention = Duration.ofDays(7);
    private Duration cleanupInterval = Duration.ofHours(1);

    /** OSS 启用时一次检查全部必填项，给出明确启动错误。 */
    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        requireText(endpoint, "OSS_ENDPOINT");
        requireText(bucketName, "OSS_BUCKET_NAME");
        requireText(accessKeyId, "OSS_ACCESS_KEY_ID");
        requireText(accessKeySecret, "OSS_ACCESS_KEY_SECRET");
        if (accessUrlTtl == null || accessUrlTtl.isZero()
                || accessUrlTtl.isNegative()) {
            throw new IllegalStateException(
                    "OSS_ACCESS_URL_TTL 必须大于0"
            );
        }
        if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
            throw new IllegalStateException("OSS_MAX_FILE_SIZE 必须大于0");
        }
        if (maxImagesPerTask <= 0) {
            throw new IllegalStateException(
                    "OSS_MAX_IMAGES_PER_TASK 必须大于0"
            );
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 未配置");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public Duration getAccessUrlTtl() {
        return accessUrlTtl;
    }

    public void setAccessUrlTtl(Duration accessUrlTtl) {
        this.accessUrlTtl = accessUrlTtl;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxImagesPerTask() {
        return maxImagesPerTask;
    }

    public void setMaxImagesPerTask(int maxImagesPerTask) {
        this.maxImagesPerTask = maxImagesPerTask;
    }

    public Duration getCleanupRetention() {
        return cleanupRetention;
    }

    public void setCleanupRetention(Duration cleanupRetention) {
        this.cleanupRetention = cleanupRetention;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }
}
