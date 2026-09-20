package com.byy.meterreading.service.impl;

import com.byy.meterreading.service.MeterImageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时重试 OSS 删除，并清理超过保留期的无效、软删除或孤立图片。 */
@Component
public class MeterImageCleanupJob {

    private final MeterImageService meterImageService;

    public MeterImageCleanupJob(MeterImageService meterImageService) {
        this.meterImageService = meterImageService;
    }

    @Scheduled(
            fixedDelayString = "${app.storage.oss.cleanup-interval:PT1H}",
            initialDelayString = "${app.storage.oss.cleanup-interval:PT1H}"
    )
    public void cleanup() {
        meterImageService.cleanupExpiredImages();
    }
}
