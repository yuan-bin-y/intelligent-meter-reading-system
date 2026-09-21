package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.dto.devicealarm.DeviceAlarmPageQueryDTO;
import com.byy.meterreading.mapper.DeviceAlarmMapper;
import com.byy.meterreading.mapper.DeviceMapper;
import com.byy.meterreading.mapper.projection.DeviceAlarmRow;
import com.byy.meterreading.model.Device;
import com.byy.meterreading.model.DeviceAlarm;
import com.byy.meterreading.model.enums.DeviceAlarmStatus;
import com.byy.meterreading.model.enums.DeviceAlarmType;
import com.byy.meterreading.model.enums.DeviceStatus;
import com.byy.meterreading.service.DeviceAlarmService;
import com.byy.meterreading.service.DeviceHeartbeatService;
import com.byy.meterreading.service.BusinessNotificationService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.devicealarm.DeviceAlarmVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 告警创建和恢复后生成管理员实时通知。 */
    private final BusinessNotificationService businessNotificationService;

    public DeviceAlarmServiceImpl(
            DeviceMapper deviceMapper,
            DeviceAlarmMapper deviceAlarmMapper,
            DeviceHeartbeatService deviceHeartbeatService,
            BusinessNotificationService businessNotificationService
    ) {
        this.deviceMapper = deviceMapper;
        this.deviceAlarmMapper = deviceAlarmMapper;
        this.deviceHeartbeatService = deviceHeartbeatService;
        this.businessNotificationService = businessNotificationService;
    }

    /**
     * 使用数据库分页完成告警条件查询，并将数据库字符串转换为稳定的枚举响应。
     */
    @Override
    @Transactional(readOnly = true)
    public PageVO<DeviceAlarmVO> listAlarms(
            DeviceAlarmPageQueryDTO queryDTO
    ) {
        validateOccurredAtRange(queryDTO);

        Page<DeviceAlarmRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<DeviceAlarmRow> result = deviceAlarmMapper.selectAlarmPage(
                page,
                queryDTO.deviceNo(),
                queryDTO.deviceName(),
                enumName(queryDTO.alarmType()),
                enumName(queryDTO.alarmStatus()),
                queryDTO.occurredAtStart(),
                queryDTO.occurredAtEnd()
        );

        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toAlarmVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceAlarmVO getAlarm(Long alarmId) {
        if (alarmId == null || alarmId <= 0) {
            throw new IllegalArgumentException("告警ID必须大于0");
        }

        DeviceAlarmRow row = deviceAlarmMapper.selectAlarmById(alarmId);
        if (row == null) {
            throw new ResourceNotFoundException("设备告警不存在");
        }
        return toAlarmVO(row);
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
                    recoverAlarm(device, openAlarm, detectedAt);
                }
                continue;
            }

            // 设备离线但已经存在 OPEN 告警时不重复插入。
            if (openAlarm == null) {
                createOfflineAlarm(device, detectedAt);
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
            Device device,
            LocalDateTime occurredAt
    ) {
        DeviceAlarm alarm = DeviceAlarm.builder()
                .deviceId(device.getId())
                .alarmType(DeviceAlarmType.OFFLINE.name())
                .alarmStatus(DeviceAlarmStatus.OPEN.name())
                .occurredAt(occurredAt)
                .build();
        try {
            deviceAlarmMapper.insert(alarm);
        } catch (DuplicateKeyException ignored) {
            // 多实例并发扫描时，由数据库唯一索引保证只保留一条 OPEN 告警。
            return;
        }
        businessNotificationService.notifyDeviceOffline(
                device,
                alarm.getId()
        );
    }

    /**
     * 恢复指定告警。更新条件包含 OPEN 状态，确保已经恢复的记录不会被重复修改。
     */
    private void recoverAlarm(
            Device device,
            DeviceAlarm alarm,
            LocalDateTime recoveredAt
    ) {
        int updated = deviceAlarmMapper.update(
                null,
                Wrappers.<DeviceAlarm>lambdaUpdate()
                        .set(DeviceAlarm::getAlarmStatus,
                                DeviceAlarmStatus.RECOVERED.name())
                        .set(DeviceAlarm::getRecoveredAt, recoveredAt)
                        .eq(DeviceAlarm::getId, alarm.getId())
                        .eq(DeviceAlarm::getAlarmStatus,
                                DeviceAlarmStatus.OPEN.name())
        );
        if (updated == 1) {
            businessNotificationService.notifyDeviceRecovered(
                    device,
                    alarm.getId()
            );
        }
    }

    /**
     * 结束时间早于开始时间时直接返回参数错误，避免执行无意义查询。
     */
    private void validateOccurredAtRange(
            DeviceAlarmPageQueryDTO queryDTO
    ) {
        if (queryDTO.occurredAtStart() != null
                && queryDTO.occurredAtEnd() != null
                && queryDTO.occurredAtEnd()
                .isBefore(queryDTO.occurredAtStart())) {
            throw new IllegalArgumentException(
                    "告警结束时间不能早于开始时间"
            );
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * 将两表关联查询结果转换成 API 响应，并补充枚举中文名称。
     */
    private DeviceAlarmVO toAlarmVO(DeviceAlarmRow row) {
        DeviceAlarmType alarmType = DeviceAlarmType.valueOf(
                row.getAlarmType()
        );
        DeviceAlarmStatus alarmStatus = DeviceAlarmStatus.valueOf(
                row.getAlarmStatus()
        );
        return new DeviceAlarmVO(
                row.getId(),
                row.getDeviceId(),
                row.getDeviceNo(),
                row.getDeviceName(),
                alarmType,
                alarmType.getDescription(),
                alarmStatus,
                alarmStatus.getDescription(),
                row.getOccurredAt(),
                row.getRecoveredAt()
        );
    }
}
