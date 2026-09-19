package com.byy.meterreading.job;

import com.byy.meterreading.service.DeviceAlarmService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时触发设备离线检测，具体检测和告警处理交给业务层完成。
 */
@Component
public class DeviceOfflineDetectionJob {

    private final DeviceAlarmService deviceAlarmService;

    public DeviceOfflineDetectionJob(DeviceAlarmService deviceAlarmService) {
        this.deviceAlarmService = deviceAlarmService;
    }

    /**
     * 上一次检测完成后等待配置的间隔，再执行下一次检测，避免任务重叠。
     */
    @Scheduled(
            fixedDelayString =
                    "${app.device.offline-detection-interval:30000}"
    )
    public void detectOfflineDevices() {
        deviceAlarmService.detectOfflineDevices();
    }
}
