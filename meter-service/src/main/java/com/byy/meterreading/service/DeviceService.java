package com.byy.meterreading.service;

import com.byy.meterreading.dto.device.CreateDeviceDTO;
import com.byy.meterreading.dto.device.DevicePageQueryDTO;
import com.byy.meterreading.dto.device.DeviceVersionDTO;
import com.byy.meterreading.dto.device.ResetDeviceSecretDTO;
import com.byy.meterreading.dto.device.UpdateDeviceDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.device.CreateDeviceVO;
import com.byy.meterreading.vo.device.DeviceDetailVO;
import com.byy.meterreading.vo.device.DeviceListItemVO;
import com.byy.meterreading.vo.device.DeviceVersionVO;
import com.byy.meterreading.vo.device.ResetDeviceSecretVO;

/**
 * 采集设备档案管理业务。
 */
public interface DeviceService {

    CreateDeviceVO createDevice(Long operatorId, CreateDeviceDTO createDTO);

    DeviceVersionVO updateDevice(
            Long operatorId,
            Long deviceId,
            UpdateDeviceDTO updateDTO
    );

    DeviceDetailVO getDevice(Long deviceId);

    ResetDeviceSecretVO resetDeviceSecret(
            Long operatorId,
            Long deviceId,
            ResetDeviceSecretDTO resetDTO
    );

    PageVO<DeviceListItemVO> listDevices(DevicePageQueryDTO queryDTO);

    DeviceVersionVO enableDevice(
            Long operatorId,
            Long deviceId,
            DeviceVersionDTO versionDTO
    );

    DeviceVersionVO disableDevice(
            Long operatorId,
            Long deviceId,
            DeviceVersionDTO versionDTO
    );

    void deleteDevice(Long operatorId, Long deviceId, Integer version);
}
