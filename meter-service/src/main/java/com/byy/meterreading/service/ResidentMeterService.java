package com.byy.meterreading.service;

import com.byy.meterreading.dto.residentmeter.BindResidentMeterDTO;
import com.byy.meterreading.dto.residentmeter.BindingResourcePageQueryDTO;
import com.byy.meterreading.dto.residentmeter.ResidentMeterPageQueryDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.residentmeter.MeterBoundResidentVO;
import com.byy.meterreading.vo.residentmeter.ResidentBoundMeterVO;
import com.byy.meterreading.vo.residentmeter.ResidentMeterBindingVO;

/**
 * 居民与表具绑定关系业务。
 */
public interface ResidentMeterService {

    ResidentMeterBindingVO bindMeter(
            Long operatorId,
            BindResidentMeterDTO bindDTO
    );

    void unbindMeter(Long operatorId, Long residentId, Long meterId);

    PageVO<ResidentMeterBindingVO> listBindings(
            ResidentMeterPageQueryDTO queryDTO
    );

    PageVO<ResidentBoundMeterVO> listResidentMeters(
            Long residentId,
            BindingResourcePageQueryDTO queryDTO
    );

    PageVO<MeterBoundResidentVO> listMeterResidents(
            Long meterId,
            BindingResourcePageQueryDTO queryDTO
    );

    PageVO<ResidentBoundMeterVO> listCurrentResidentMeters(
            Long currentResidentId,
            BindingResourcePageQueryDTO queryDTO
    );

    boolean hasBindingsByMeterId(Long meterId);

    boolean hasBindingsByResidentId(Long residentId);
}
