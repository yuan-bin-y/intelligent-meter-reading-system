package com.byy.meterreading.service;

import com.byy.meterreading.model.enums.MeterImageType;

/** Controller 将 multipart 文件转换成与 Web 层无关的上传命令。 */
public record MeterImageUploadCommand(
        String requestId,
        MeterImageType imageType,
        String originalName,
        String declaredContentType,
        byte[] content
) {
}
