package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.dto.device.CreateDeviceDTO;
import com.byy.meterreading.dto.device.DevicePageQueryDTO;
import com.byy.meterreading.dto.device.DeviceVersionDTO;
import com.byy.meterreading.dto.device.ResetDeviceSecretDTO;
import com.byy.meterreading.dto.device.UpdateDeviceDTO;
import com.byy.meterreading.mapper.DeviceMapper;
import com.byy.meterreading.model.Device;
import com.byy.meterreading.model.enums.DeviceStatus;
import com.byy.meterreading.model.enums.DeviceType;
import com.byy.meterreading.service.DeviceCredentialService;
import com.byy.meterreading.service.DeviceHeartbeatService;
import com.byy.meterreading.service.DeviceMeterService;
import com.byy.meterreading.service.DeviceService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.device.CreateDeviceVO;
import com.byy.meterreading.vo.device.DeviceDetailVO;
import com.byy.meterreading.vo.device.DeviceListItemVO;
import com.byy.meterreading.vo.device.DeviceRuntimeStatusVO;
import com.byy.meterreading.vo.device.DeviceVersionVO;
import com.byy.meterreading.vo.device.ResetDeviceSecretVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 采集设备档案管理业务实现。
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    private static final int INITIAL_CREDENTIAL_VERSION = 1;

    private final DeviceMapper deviceMapper;
    private final DeviceMeterService deviceMeterService;
    private final DeviceCredentialService deviceCredentialService;
    private final DeviceHeartbeatService deviceHeartbeatService;

    public DeviceServiceImpl(
            DeviceMapper deviceMapper,
            DeviceMeterService deviceMeterService,
            DeviceCredentialService deviceCredentialService,
            DeviceHeartbeatService deviceHeartbeatService
    ) {
        this.deviceMapper = deviceMapper;
        this.deviceMeterService = deviceMeterService;
        this.deviceCredentialService = deviceCredentialService;
        this.deviceHeartbeatService = deviceHeartbeatService;
    }

    /**
     * 新建设备默认停用，设备编号和类型创建后不通过资料接口修改。
     */
    @Override
    public CreateDeviceVO createDevice(
            Long operatorId,
            CreateDeviceDTO createDTO
    ) {
        requireOperatorId(operatorId);
        String deviceNo = createDTO.deviceNo().trim();
        String deviceName = createDTO.deviceName().trim();
        String remark = normalizeOptionalText(createDTO.remark());

        if (existsByDeviceNo(deviceNo)) {
            throw new ResourceConflictException("设备编号已存在");
        }

        DeviceCredentialService.GeneratedCredential credential =
                deviceCredentialService.generate();
        LocalDateTime credentialGeneratedAt = LocalDateTime.now();

        Device device = Device.builder()
                .deviceNo(deviceNo)
                .deviceName(deviceName)
                .deviceType(createDTO.deviceType().name())
                .status(DeviceStatus.DISABLED.getCode())
                .version(0)
                .secretHash(credential.secretHash())
                .credentialVersion(INITIAL_CREDENTIAL_VERSION)
                .secretRotatedAt(credentialGeneratedAt)
                .remark(remark)
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .deleted(0)
                .build();
        try {
            deviceMapper.insert(device);
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException(
                    "设备编号已存在",
                exception
            );
        }
        return new CreateDeviceVO(
                device.getId(),
                device.getDeviceNo(),
                credential.rawSecret(),
                device.getCredentialVersion()
        );
    }

    @Override
    public DeviceVersionVO updateDevice(
            Long operatorId,
            Long deviceId,
            UpdateDeviceDTO updateDTO
    ) {
        requireOperatorId(operatorId);
        Device currentDevice = requireDevice(deviceId);
        requireExpectedVersion(currentDevice, updateDTO.version());

        LambdaUpdateWrapper<Device> updateWrapper =
                Wrappers.<Device>lambdaUpdate()
                        .set(Device::getDeviceName,
                                updateDTO.deviceName().trim())
                        .set(Device::getRemark,
                                normalizeOptionalText(updateDTO.remark()))
                        .set(Device::getUpdatedBy, operatorId)
                        .setSql("version = version + 1")
                        .eq(Device::getId, deviceId)
                        .eq(Device::getVersion, updateDTO.version());

        int updatedRows = deviceMapper.update(null, updateWrapper);
        ensureUpdated(updatedRows, deviceId);
        return new DeviceVersionVO(deviceId, updateDTO.version() + 1);
    }

    @Override
    public DeviceDetailVO getDevice(Long deviceId) {
        return toDetailVO(requireDevice(deviceId));
    }

    @Override
    public DeviceRuntimeStatusVO getRuntimeStatus(Long deviceId) {
        Device device = requireDevice(deviceId);
        return new DeviceRuntimeStatusVO(
                device.getId(),
                device.getDeviceNo(),
                deviceHeartbeatService.isOnline(device.getId())
        );
    }

    /**
     * 原子更新密钥摘要及两个版本号，清理旧凭证产生的心跳；
     * 明文密钥只通过本次响应返回。
     */
    @Override
    public ResetDeviceSecretVO resetDeviceSecret(
            Long operatorId,
            Long deviceId,
            ResetDeviceSecretDTO resetDTO
    ) {
        requireOperatorId(operatorId);
        Device currentDevice = requireDevice(deviceId);
        requireExpectedVersion(currentDevice, resetDTO.version());

        DeviceCredentialService.GeneratedCredential credential =
                deviceCredentialService.generate();
        int currentCredentialVersion = currentDevice.getCredentialVersion() == null
                ? 0
                : currentDevice.getCredentialVersion();
        int nextCredentialVersion = currentCredentialVersion + 1;
        LocalDateTime rotatedAt = LocalDateTime.now();

        LambdaUpdateWrapper<Device> updateWrapper =
                Wrappers.<Device>lambdaUpdate()
                        .set(Device::getSecretHash, credential.secretHash())
                        .set(Device::getCredentialVersion,
                                nextCredentialVersion)
                        .set(Device::getSecretRotatedAt, rotatedAt)
                        .set(Device::getUpdatedBy, operatorId)
                        .setSql("version = version + 1")
                        .eq(Device::getId, deviceId)
                        .eq(Device::getVersion, resetDTO.version());
        int updatedRows = deviceMapper.update(null, updateWrapper);
        ensureUpdated(updatedRows, deviceId);

        // 旧心跳由旧密钥认证产生，重置密钥后必须立即失效。
        deviceHeartbeatService.clearHeartbeat(deviceId);

        return new ResetDeviceSecretVO(
                deviceId,
                currentDevice.getDeviceNo(),
                credential.rawSecret(),
                nextCredentialVersion,
                resetDTO.version() + 1,
                rotatedAt
        );
    }

    @Override
    public PageVO<DeviceListItemVO> listDevices(
            DevicePageQueryDTO queryDTO
    ) {
        LambdaQueryWrapper<Device> queryWrapper =
                Wrappers.<Device>lambdaQuery()
                        .and(queryDTO.keyword() != null, wrapper -> wrapper
                                .like(Device::getDeviceNo,
                                        queryDTO.keyword())
                                .or()
                                .like(Device::getDeviceName,
                                        queryDTO.keyword())
                        )
                        .eq(queryDTO.deviceType() != null,
                                Device::getDeviceType,
                                queryDTO.deviceType() == null
                                        ? null
                                        : queryDTO.deviceType().name())
                        .eq(queryDTO.status() != null,
                                Device::getStatus,
                                queryDTO.status())
                        .orderByDesc(Device::getCreatedAt)
                        .orderByDesc(Device::getId);

        IPage<Device> result = deviceMapper.selectPage(
                new Page<>(queryDTO.page(), queryDTO.pageSize()),
                queryWrapper
        );
        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toListItemVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    public DeviceVersionVO enableDevice(
            Long operatorId,
            Long deviceId,
            DeviceVersionDTO versionDTO
    ) {
        return updateStatus(
                operatorId,
                deviceId,
                versionDTO.version(),
                DeviceStatus.ENABLED
        );
    }

    @Override
    public DeviceVersionVO disableDevice(
            Long operatorId,
            Long deviceId,
            DeviceVersionDTO versionDTO
    ) {
        DeviceVersionVO result = updateStatus(
                operatorId,
                deviceId,
                versionDTO.version(),
                DeviceStatus.DISABLED
        );
        // 设备停用后不能继续保持在线状态。
        deviceHeartbeatService.clearHeartbeat(deviceId);
        return result;
    }

    /**
     * 设备必须先停用且解除全部表具绑定后才能逻辑删除。
     */
    @Override
    public void deleteDevice(
            Long operatorId,
            Long deviceId,
            Integer version
    ) {
        requireOperatorId(operatorId);
        if (version == null || version < 0) {
            throw new IllegalArgumentException("数据版本不能为空且不能小于0");
        }
        Device currentDevice = requireDevice(deviceId);
        requireExpectedVersion(currentDevice, version);
        if (!Integer.valueOf(DeviceStatus.DISABLED.getCode())
                .equals(currentDevice.getStatus())) {
            throw new IllegalArgumentException("只有停用状态的设备可以删除");
        }
        if (deviceMeterService.hasBindingsByDeviceId(deviceId)) {
            throw new ResourceConflictException(
                    "设备仍绑定表具，不能删除"
            );
        }

        LambdaUpdateWrapper<Device> updateWrapper =
                Wrappers.<Device>lambdaUpdate()
                        .set(Device::getDeleted, 1)
                        .set(Device::getUpdatedBy, operatorId)
                        .setSql("version = version + 1")
                        .eq(Device::getId, deviceId)
                        .eq(Device::getVersion, version);
        int updatedRows = deviceMapper.update(null, updateWrapper);
        ensureUpdated(updatedRows, deviceId);

        // 删除设备后同步清理 Redis 中残留的运行状态。
        deviceHeartbeatService.clearHeartbeat(deviceId);
    }

    private DeviceVersionVO updateStatus(
            Long operatorId,
            Long deviceId,
            Integer expectedVersion,
            DeviceStatus targetStatus
    ) {
        requireOperatorId(operatorId);
        Device currentDevice = requireDevice(deviceId);
        requireExpectedVersion(currentDevice, expectedVersion);
        if (Integer.valueOf(targetStatus.getCode())
                .equals(currentDevice.getStatus())) {
            throw new IllegalArgumentException(
                    "设备已经处于" + targetStatus.getDescription() + "状态"
            );
        }

        LambdaUpdateWrapper<Device> updateWrapper =
                Wrappers.<Device>lambdaUpdate()
                        .set(Device::getStatus, targetStatus.getCode())
                        .set(Device::getUpdatedBy, operatorId)
                        .setSql("version = version + 1")
                        .eq(Device::getId, deviceId)
                        .eq(Device::getVersion, expectedVersion);
        int updatedRows = deviceMapper.update(null, updateWrapper);
        ensureUpdated(updatedRows, deviceId);
        return new DeviceVersionVO(deviceId, expectedVersion + 1);
    }

    private boolean existsByDeviceNo(String deviceNo) {
        return deviceMapper.selectCount(
                Wrappers.<Device>lambdaQuery()
                        .eq(Device::getDeviceNo, deviceNo)
        ) > 0;
    }

    private Device requireDevice(Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("设备ID必须大于0");
        }
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new ResourceNotFoundException("设备不存在");
        }
        return device;
    }

    private void requireOperatorId(Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("当前操作人不能为空");
        }
    }

    private void requireExpectedVersion(
            Device device,
            Integer expectedVersion
    ) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("数据版本不能为空且不能小于0");
        }
        if (!expectedVersion.equals(device.getVersion())) {
            throw new VersionConflictException(
                    "设备信息已被其他用户修改，请刷新后重试"
            );
        }
    }

    private void ensureUpdated(int updatedRows, Long deviceId) {
        if (updatedRows == 1) {
            return;
        }
        if (deviceMapper.selectById(deviceId) == null) {
            throw new ResourceNotFoundException("设备不存在");
        }
        throw new VersionConflictException(
                "设备信息已被其他用户修改，请刷新后重试"
        );
    }

    private DeviceListItemVO toListItemVO(Device device) {
        return new DeviceListItemVO(
                device.getId(),
                device.getDeviceNo(),
                device.getDeviceName(),
                DeviceType.valueOf(device.getDeviceType()),
                device.getStatus(),
                device.getVersion(),
                device.getUpdatedAt()
        );
    }

    private DeviceDetailVO toDetailVO(Device device) {
        return new DeviceDetailVO(
                device.getId(),
                device.getDeviceNo(),
                device.getDeviceName(),
                DeviceType.valueOf(device.getDeviceType()),
                device.getStatus(),
                device.getVersion(),
                device.getSecretHash() != null
                        && !device.getSecretHash().isBlank(),
                device.getCredentialVersion(),
                device.getSecretRotatedAt(),
                device.getRemark(),
                device.getCreatedBy(),
                device.getUpdatedBy(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
