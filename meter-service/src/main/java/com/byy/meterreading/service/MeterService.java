package com.byy.meterreading.service;

import com.byy.meterreading.dto.meter.CreateMeterDTO;
import com.byy.meterreading.vo.meter.CreateMeterVO;

/**
 * 表具管理业务接口。
 */
public interface MeterService {

    /**
     * 新增表具。
     *
     * @param operatorId    当前管理员用户主键
     * @param createMeterDTO 新增表具请求
     * @return 新增成功后的表具标识
     */
    CreateMeterVO createMeter(
            Long operatorId,
            CreateMeterDTO createMeterDTO
    );
}
