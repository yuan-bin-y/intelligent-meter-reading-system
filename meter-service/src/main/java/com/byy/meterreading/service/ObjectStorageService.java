package com.byy.meterreading.service;

/**
 * 业务层使用的对象存储抽象，隔离具体 OSS SDK 调用。
 */
public interface ObjectStorageService {

    boolean isEnabled();

    StoredObject upload(
            String objectKey,
            byte[] content,
            String contentType
    );

    String generateAccessUrl(String bucketName, String objectKey);

    void delete(String bucketName, String objectKey);

    record StoredObject(
            String bucketName,
            String objectKey,
            String eTag
    ) {
    }
}
