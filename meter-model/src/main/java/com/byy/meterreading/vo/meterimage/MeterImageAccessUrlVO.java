package com.byy.meterreading.vo.meterimage;

/** 私有 OSS 图片的短期访问地址。 */
public record MeterImageAccessUrlVO(
        Long imageId,
        String accessUrl,
        long expiresIn
) {
}
