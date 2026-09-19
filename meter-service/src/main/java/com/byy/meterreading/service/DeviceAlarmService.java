package com.byy.meterreading.service;

import com.byy.meterreading.dto.devicealarm.DeviceAlarmPageQueryDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.devicealarm.DeviceAlarmVO;

/**
 * 设备离线检测与告警状态处理业务。
 */
public interface DeviceAlarmService {

    /**
     * 管理员按条件分页查询设备告警。
     */
    PageVO<DeviceAlarmVO> listAlarms(
            DeviceAlarmPageQueryDTO queryDTO
    );

    /**
     * 查询一条设备告警详情。
     */
    DeviceAlarmVO getAlarm(Long alarmId);

    /**
     * 扫描启用设备，根据 Redis 心跳创建离线告警或恢复已有告警。
     */
    void detectOfflineDevices();
}
