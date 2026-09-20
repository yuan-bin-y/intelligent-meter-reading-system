package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 抄表图片 OSS 元数据实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("meter_image")
public class MeterImage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String uploaderType;
    private Long uploaderId;
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

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
