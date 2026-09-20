package com.byy.meterreading.vo.meterimage;

import com.byy.meterreading.model.enums.MeterImageStatus;
import com.byy.meterreading.model.enums.MeterImageStorageStatus;
import com.byy.meterreading.model.enums.MeterImageType;

import java.time.LocalDateTime;

/** 上传响应以及任务图片列表共用的安全图片信息。 */
public record MeterImageItemVO(
        Long imageId,
        Long taskId,
        MeterImageType imageType,
        String imageTypeName,
        String originalName,
        String contentType,
        Long fileSize,
        Integer imageWidth,
        Integer imageHeight,
        MeterImageStatus imageStatus,
        String imageStatusName,
        MeterImageStorageStatus storageStatus,
        Long resultId,
        Integer version,
        LocalDateTime createdAt
) {
}
