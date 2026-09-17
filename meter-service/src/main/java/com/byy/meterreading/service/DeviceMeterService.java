package com.byy.meterreading.service;

import com.byy.meterreading.dto.devicemeter.BindDeviceMeterDTO;
import com.byy.meterreading.dto.devicemeter.DeviceMeterPageQueryDTO;
import com.byy.meterreading.dto.devicemeter.DeviceMeterResourcePageQueryDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.devicemeter.DeviceBoundMeterVO;
import com.byy.meterreading.vo.devicemeter.DeviceMeterBindingVO;
import com.byy.meterreading.vo.devicemeter.MeterBoundDeviceVO;

/**
 * 设备与表具绑定关系业务。
 */
public interface DeviceMeterService {

    DeviceMeterBindingVO bindMeter(
            Long operatorId,
            BindDeviceMeterDTO bindDTO
    );

    void unbindMeter(Long operatorId, Long deviceId, Long meterId);

    PageVO<DeviceMeterBindingVO> listBindings(
            DeviceMeterPageQueryDTO queryDTO
    );

    PageVO<DeviceBoundMeterVO> listDeviceMeters(
            Long deviceId,
            DeviceMeterResourcePageQueryDTO queryDTO
    );

    PageVO<MeterBoundDeviceVO> listMeterDevices(
            Long meterId,
            DeviceMeterResourcePageQueryDTO queryDTO
    );

    boolean hasBindingsByDeviceId(Long deviceId);

    boolean hasBindingsByMeterId(Long meterId);
}
