package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 图片、任务、表具、上传者和结果关系的关联查询投影。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeterImageRow {
    private Long imageId;
    private Long taskId;
    private String taskNo;
    private String taskStatus;
    private Long meterId;
    private String meterNo;
    private String meterName;
    private String uploaderType;
    private Long uploaderId;
    private String uploaderCode;
    private String uploaderName;
    private String uploadRequestId;
    private String imageType;
    private String originalName;
    private String bucketName;
    private String objectKey;
    private String ossEtag;
    private String contentType;
    private Long fileSize;
    private Integer imageWidth;
    private Integer imageHeight;
    private String sha256;
    private String imageStatus;
    private String storageStatus;
    private String statusReason;
    private Long statusChangedBy;
    private LocalDateTime statusChangedAt;
    private Integer deleted;
    private Long deletedBy;
    private LocalDateTime deletedAt;
    private Long resultId;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
