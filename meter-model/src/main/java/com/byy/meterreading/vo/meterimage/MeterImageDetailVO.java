package com.byy.meterreading.vo.meterimage;

import com.byy.meterreading.model.enums.MeterImageStatus;
import com.byy.meterreading.model.enums.MeterImageStorageStatus;
import com.byy.meterreading.model.enums.MeterImageType;
import com.byy.meterreading.model.enums.TaskExecutorType;

import java.time.LocalDateTime;

/** 管理员查看的完整图片元数据和业务关联信息。 */
public record MeterImageDetailVO(
        Long imageId,
        Long taskId,
        String taskNo,
        String taskStatus,
        Long meterId,
        String meterNo,
        String meterName,
        TaskExecutorType uploaderType,
        String uploaderTypeName,
        Long uploaderId,
        String uploaderCode,
        String uploaderName,
        MeterImageType imageType,
        String imageTypeName,
        String originalName,
        String bucketName,
        String objectKey,
        String ossEtag,
        String contentType,
        Long fileSize,
        Integer imageWidth,
        Integer imageHeight,
        String sha256,
        MeterImageStatus imageStatus,
        String imageStatusName,
        MeterImageStorageStatus storageStatus,
        String storageStatusName,
        String statusReason,
        Long statusChangedBy,
        LocalDateTime statusChangedAt,
        boolean deleted,
        Long deletedBy,
        LocalDateTime deletedAt,
        Long resultId,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
