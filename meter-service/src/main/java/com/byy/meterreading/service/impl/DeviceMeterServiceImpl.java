package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.dto.devicemeter.BindDeviceMeterDTO;
import com.byy.meterreading.dto.devicemeter.DeviceMeterPageQueryDTO;
import com.byy.meterreading.dto.devicemeter.DeviceMeterResourcePageQueryDTO;
import com.byy.meterreading.mapper.DeviceMapper;
import com.byy.meterreading.mapper.DeviceMeterMapper;
import com.byy.meterreading.mapper.MeterMapper;
import com.byy.meterreading.mapper.projection.DeviceBoundMeterRow;
import com.byy.meterreading.mapper.projection.DeviceMeterBindingRow;
import com.byy.meterreading.mapper.projection.MeterBoundDeviceRow;
import com.byy.meterreading.model.Device;
import com.byy.meterreading.model.DeviceMeter;
import com.byy.meterreading.model.Meter;
import com.byy.meterreading.model.enums.DeviceStatus;
import com.byy.meterreading.model.enums.DeviceType;
import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterStatus;
import com.byy.meterreading.model.enums.MeterType;
import com.byy.meterreading.service.DeviceMeterService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.devicemeter.DeviceBoundMeterVO;
import com.byy.meterreading.vo.devicemeter.DeviceMeterBindingVO;
import com.byy.meterreading.vo.devicemeter.MeterBoundDeviceVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 设备与表具绑定关系业务实现。
 */
@Service
public class DeviceMeterServiceImpl implements DeviceMeterService {

    private final DeviceMeterMapper deviceMeterMapper;
    private final DeviceMapper deviceMapper;
    private final MeterMapper meterMapper;

    public DeviceMeterServiceImpl(
            DeviceMeterMapper deviceMeterMapper,
            DeviceMapper deviceMapper,
            MeterMapper meterMapper
    ) {
        this.deviceMeterMapper = deviceMeterMapper;
        this.deviceMapper = deviceMapper;
        this.meterMapper = meterMapper;
    }

    /**
     * 只有启用设备和正常表具可以建立关系，联合主键处理并发重复绑定。
     */
    @Override
    @Transactional
    public DeviceMeterBindingVO bindMeter(
            Long operatorId,
            BindDeviceMeterDTO bindDTO
    ) {
        requireOperatorId(operatorId);
        Device device = requireDevice(bindDTO.deviceId());
        Meter meter = requireMeter(bindDTO.meterId());

        if (!Integer.valueOf(DeviceStatus.ENABLED.getCode())
                .equals(device.getStatus())) {
            throw new IllegalArgumentException("只有启用状态的设备可以绑定表具");
        }
        if (MeterStatus.fromCode(meter.getStatus()) != MeterStatus.ACTIVE) {
            throw new IllegalArgumentException("只有正常状态的表具可以绑定设备");
        }
        if (existsBinding(bindDTO.deviceId(), bindDTO.meterId())) {
            throw new ResourceConflictException("设备已经绑定该表具");
        }

        LocalDateTime boundAt = LocalDateTime.now();
        DeviceMeter binding = DeviceMeter.builder()
                .deviceId(bindDTO.deviceId())
                .meterId(bindDTO.meterId())
                .createdBy(operatorId)
                .createdAt(boundAt)
                .build();
        try {
            deviceMeterMapper.insert(binding);
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException(
                    "设备已经绑定该表具",
                    exception
            );
        }
        return toBindingVO(device, meter, operatorId, boundAt);
    }

    @Override
    @Transactional
    public void unbindMeter(
            Long operatorId,
            Long deviceId,
            Long meterId
    ) {
        requireOperatorId(operatorId);
        requirePositiveId(deviceId, "设备ID必须大于0");
        requirePositiveId(meterId, "表具ID必须大于0");

        int deletedRows = deviceMeterMapper.delete(
                Wrappers.<DeviceMeter>lambdaQuery()
                        .eq(DeviceMeter::getDeviceId, deviceId)
                        .eq(DeviceMeter::getMeterId, meterId)
        );
        if (deletedRows != 1) {
            throw new ResourceNotFoundException("设备表具绑定关系不存在");
        }
    }

    /**
     * 管理员查询完整绑定关系，底层使用三表 JOIN 和数据库分页。
     */
    @Override
    @Transactional(readOnly = true)
    public PageVO<DeviceMeterBindingVO> listBindings(
            DeviceMeterPageQueryDTO queryDTO
    ) {
        Page<DeviceMeterBindingRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<DeviceMeterBindingRow> result =
                deviceMeterMapper.selectBindingPage(
                        page,
                        queryDTO.deviceId(),
                        queryDTO.meterId(),
                        queryDTO.keyword()
                );
        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toBindingVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<DeviceBoundMeterVO> listDeviceMeters(
            Long deviceId,
            DeviceMeterResourcePageQueryDTO queryDTO
    ) {
        requireDevice(deviceId);
        Page<DeviceBoundMeterRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<DeviceBoundMeterRow> result =
                deviceMeterMapper.selectDeviceMeterPage(
                        page,
                        deviceId,
                        queryDTO.keyword()
                );
        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toDeviceBoundMeterVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<MeterBoundDeviceVO> listMeterDevices(
            Long meterId,
            DeviceMeterResourcePageQueryDTO queryDTO
    ) {
        requireMeter(meterId);
        Page<MeterBoundDeviceRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<MeterBoundDeviceRow> result =
                deviceMeterMapper.selectMeterDevicePage(
                        page,
                        meterId,
                        queryDTO.keyword()
                );
        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toMeterBoundDeviceVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    public boolean hasBindingsByDeviceId(Long deviceId) {
        return deviceId != null && deviceMeterMapper.selectCount(
                Wrappers.<DeviceMeter>lambdaQuery()
                        .eq(DeviceMeter::getDeviceId, deviceId)
        ) > 0;
    }

    @Override
    public boolean hasBindingsByMeterId(Long meterId) {
        return meterId != null && deviceMeterMapper.selectCount(
                Wrappers.<DeviceMeter>lambdaQuery()
                        .eq(DeviceMeter::getMeterId, meterId)
        ) > 0;
    }

    private Device requireDevice(Long deviceId) {
        requirePositiveId(deviceId, "设备ID必须大于0");
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new ResourceNotFoundException("设备不存在");
        }
        return device;
    }

    private Meter requireMeter(Long meterId) {
        requirePositiveId(meterId, "表具ID必须大于0");
        Meter meter = meterMapper.selectById(meterId);
        if (meter == null) {
            throw new ResourceNotFoundException("表具不存在");
        }
        return meter;
    }

    private boolean existsBinding(Long deviceId, Long meterId) {
        return deviceMeterMapper.selectCount(
                Wrappers.<DeviceMeter>lambdaQuery()
                        .eq(DeviceMeter::getDeviceId, deviceId)
                        .eq(DeviceMeter::getMeterId, meterId)
        ) > 0;
    }

    private void requireOperatorId(Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("当前操作人不能为空");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private DeviceMeterBindingVO toBindingVO(
            Device device,
            Meter meter,
            Long createdBy,
            LocalDateTime boundAt
    ) {
        return new DeviceMeterBindingVO(
                device.getId(),
                device.getDeviceNo(),
                device.getDeviceName(),
                DeviceType.valueOf(device.getDeviceType()),
                device.getStatus(),
                meter.getId(),
                meter.getMeterNo(),
                meter.getMeterName(),
                MeterType.valueOf(meter.getMeterType()),
                MeterDisplayType.valueOf(meter.getDisplayType()),
                meter.getUnit(),
                meter.getStatus(),
                createdBy,
                boundAt
        );
    }

    private DeviceMeterBindingVO toBindingVO(DeviceMeterBindingRow row) {
        return new DeviceMeterBindingVO(
                row.getDeviceId(),
                row.getDeviceNo(),
                row.getDeviceName(),
                DeviceType.valueOf(row.getDeviceType()),
                row.getDeviceStatus(),
                row.getMeterId(),
                row.getMeterNo(),
                row.getMeterName(),
                MeterType.valueOf(row.getMeterType()),
                MeterDisplayType.valueOf(row.getDisplayType()),
                row.getUnit(),
                row.getMeterStatus(),
                row.getCreatedBy(),
                row.getBoundAt()
        );
    }

    private DeviceBoundMeterVO toDeviceBoundMeterVO(
            DeviceBoundMeterRow row
    ) {
        return new DeviceBoundMeterVO(
                row.getMeterId(),
                row.getMeterNo(),
                row.getMeterName(),
                MeterType.valueOf(row.getMeterType()),
                MeterDisplayType.valueOf(row.getDisplayType()),
                row.getUnit(),
                row.getStatus(),
                row.getVersion(),
                row.getBoundAt()
        );
    }

    private MeterBoundDeviceVO toMeterBoundDeviceVO(
            MeterBoundDeviceRow row
    ) {
        return new MeterBoundDeviceVO(
                row.getDeviceId(),
                row.getDeviceNo(),
                row.getDeviceName(),
                DeviceType.valueOf(row.getDeviceType()),
                row.getStatus(),
                row.getVersion(),
                row.getBoundAt()
        );
    }
}
