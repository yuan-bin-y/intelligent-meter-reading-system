package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.byy.meterreading.mapper.DeviceAlarmMapper;
import com.byy.meterreading.mapper.DeviceMapper;
import com.byy.meterreading.model.Device;
import com.byy.meterreading.model.DeviceAlarm;
import com.byy.meterreading.model.enums.DeviceAlarmStatus;
import com.byy.meterreading.model.enums.DeviceAlarmType;
import com.byy.meterreading.model.enums.DeviceStatus;
import com.byy.meterreading.service.DeviceAlarmService;
import com.byy.meterreading.service.DeviceHeartbeatService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备离线检测与告警状态处理实现。
 *
 * <p>定时任务调用本服务后，服务会查询所有启用设备并检查 Redis 心跳：
 * 心跳不存在时创建离线告警，心跳重新出现时恢复已有离线告警。</p>
 */
@Service
public class DeviceAlarmServiceImpl implements DeviceAlarmService {

    /** 查询需要参与离线检测的设备档案。 */
    private final DeviceMapper deviceMapper;

    /** 查询、创建和恢复设备告警记录。 */
    private final DeviceAlarmMapper deviceAlarmMapper;

    /** 根据 Redis 心跳 Key 判断设备当前是否在线。 */
    private final DeviceHeartbeatService deviceHeartbeatService;

    public DeviceAlarmServiceImpl(
            DeviceMapper deviceMapper,
            DeviceAlarmMapper deviceAlarmMapper,
            DeviceHeartbeatService deviceHeartbeatService
    ) {
        this.deviceMapper = deviceMapper;
        this.deviceAlarmMapper = deviceAlarmMapper;
        this.deviceHeartbeatService = deviceHeartbeatService;
    }

    /**
     * 扫描所有启用设备，并根据当前心跳状态创建或恢复离线告警。
     *
     * <p>Redis 异常会直接向上抛出并中断本轮扫描，不能把 Redis
     * 本身不可用误判成所有设备同时离线。已经处理完成的单条数据库
     * 操作保持原结果，剩余设备等待下一轮定时任务继续检测。</p>
     */
    @Override
    public void detectOfflineDevices() {
        // 1. 只检测管理员已经启用的设备；MyBatis-Plus 会自动排除逻辑删除数据。
        List<Device> enabledDevices = deviceMapper.selectList(
                Wrappers.<Device>lambdaQuery()
                        .eq(Device::getStatus,
                                DeviceStatus.ENABLED.getCode())
        );
        if (enabledDevices.isEmpty()) {
            return;
        }

        // 2. 一次加载现有 OPEN 告警，避免循环中为每台设备重复查询 MySQL。
        Map<Long, DeviceAlarm> openAlarmByDevice =
                loadOpenOfflineAlarms();

        // 同一轮扫描统一使用一个检测时间，便于查看和统计本轮处理结果。
        LocalDateTime detectedAt = LocalDateTime.now();

        // 3. 逐台检查 Redis 心跳，并根据“在线状态 + 现有告警”执行状态流转。
        for (Device device : enabledDevices) {
            DeviceAlarm openAlarm = openAlarmByDevice.get(device.getId());
            boolean online = deviceHeartbeatService.isOnline(device.getId());

            if (online) {
                // 设备恢复心跳且存在未恢复告警时，将该告警更新为 RECOVERED。
                if (openAlarm != null) {
                    recoverAlarm(openAlarm.getId(), detectedAt);
                }
                continue;
            }

            // 设备离线但已经存在 OPEN 告警时不重复插入。
            if (openAlarm == null) {
                createOfflineAlarm(device.getId(), detectedAt);
            }
        }
    }

    /**
     * 查询全部未恢复的离线告警，并按设备 ID 建立索引。
     * 数据库唯一索引正常情况下保证一台设备只有一条 OPEN 离线告警，
     * putIfAbsent 进一步避免异常历史数据覆盖先读取到的记录。
     */
    private Map<Long, DeviceAlarm> loadOpenOfflineAlarms() {
        List<DeviceAlarm> openAlarms = deviceAlarmMapper.selectList(
                Wrappers.<DeviceAlarm>lambdaQuery()
                        .eq(DeviceAlarm::getAlarmType,
                                DeviceAlarmType.OFFLINE.name())
                        .eq(DeviceAlarm::getAlarmStatus,
                                DeviceAlarmStatus.OPEN.name())
        );

        Map<Long, DeviceAlarm> result = new HashMap<>();
        for (DeviceAlarm alarm : openAlarms) {
            result.putIfAbsent(alarm.getDeviceId(), alarm);
        }
        return result;
    }

    /**
     * 为离线设备创建一条 OPEN 告警。
     * 多实例可能同时判断同一设备离线，最终由数据库唯一索引保证幂等。
     */
    private void createOfflineAlarm(
            Long deviceId,
            LocalDateTime occurredAt
    ) {
        DeviceAlarm alarm = DeviceAlarm.builder()
                .deviceId(deviceId)
                .alarmType(DeviceAlarmType.OFFLINE.name())
                .alarmStatus(DeviceAlarmStatus.OPEN.name())
                .occurredAt(occurredAt)
                .build();
        try {
            deviceAlarmMapper.insert(alarm);
        } catch (DuplicateKeyException ignored) {
            // 多实例并发扫描时，由数据库唯一索引保证只保留一条 OPEN 告警。
        }
    }

    /**
     * 恢复指定告警。更新条件包含 OPEN 状态，确保已经恢复的记录不会被重复修改。
     */
    private void recoverAlarm(
            Long alarmId,
            LocalDateTime recoveredAt
    ) {
        deviceAlarmMapper.update(
                null,
                Wrappers.<DeviceAlarm>lambdaUpdate()
                        .set(DeviceAlarm::getAlarmStatus,
                                DeviceAlarmStatus.RECOVERED.name())
                        .set(DeviceAlarm::getRecoveredAt, recoveredAt)
                        .eq(DeviceAlarm::getId, alarmId)
                        .eq(DeviceAlarm::getAlarmStatus,
                                DeviceAlarmStatus.OPEN.name())
        );
    }
}
