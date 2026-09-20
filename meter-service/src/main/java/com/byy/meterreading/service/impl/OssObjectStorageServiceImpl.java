package com.byy.meterreading.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.byy.meterreading.common.exception.ObjectStorageException;
import com.byy.meterreading.config.OssProperties;
import com.byy.meterreading.service.ObjectStorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;

/**
 * 使用阿里云 OSS Java SDK 完成上传、私有对象签名和删除。
 */
@Service
public class OssObjectStorageServiceImpl implements ObjectStorageService {

    private final ObjectProvider<OSS> ossProvider;
    private final OssProperties properties;

    public OssObjectStorageServiceImpl(
            ObjectProvider<OSS> ossProvider,
            OssProperties properties
    ) {
        this.ossProvider = ossProvider;
        this.properties = properties;
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled() && ossProvider.getIfAvailable() != null;
    }

    @Override
    public StoredObject upload(
            String objectKey,
            byte[] content,
            String contentType
    ) {
        OSS oss = requireClient();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            PutObjectResult result = oss.putObject(
                    new PutObjectRequest(
                            properties.getBucketName(),
                            objectKey,
                            input,
                            metadata
                    )
            );
            return new StoredObject(
                    properties.getBucketName(),
                    objectKey,
                    result.getETag()
            );
        } catch (Exception exception) {
            throw new ObjectStorageException("图片上传 OSS 失败", exception);
        }
    }

    @Override
    public String generateAccessUrl(
            String bucketName,
            String objectKey
    ) {
        OSS oss = requireClient();
        Date expiration = Date.from(
                Instant.now().plus(properties.getAccessUrlTtl())
        );
        try {
            return oss.generatePresignedUrl(
                    bucketName,
                    objectKey,
                    expiration
            ).toString();
        } catch (Exception exception) {
            throw new ObjectStorageException(
                    "生成 OSS 图片访问地址失败",
                    exception
            );
        }
    }

    @Override
    public void delete(String bucketName, String objectKey) {
        OSS oss = requireClient();
        try {
            oss.deleteObject(bucketName, objectKey);
        } catch (Exception exception) {
            throw new ObjectStorageException("删除 OSS 图片失败", exception);
        }
    }

    private OSS requireClient() {
        if (!properties.isEnabled()) {
            throw new ObjectStorageException(
                    "OSS 尚未启用，请配置 OSS_ENABLED=true 及访问凭证"
            );
        }
        OSS oss = ossProvider.getIfAvailable();
        if (oss == null) {
            throw new ObjectStorageException("OSS 客户端未正确初始化");
        }
        return oss;
    }
}
