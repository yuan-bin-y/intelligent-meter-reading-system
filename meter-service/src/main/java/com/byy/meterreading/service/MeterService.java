package com.byy.meterreading.service;

import com.byy.meterreading.dto.meter.CreateMeterDTO;
import com.byy.meterreading.dto.meter.MeterPageQueryDTO;
import com.byy.meterreading.dto.meter.UpdateMeterDTO;
import com.byy.meterreading.dto.meter.UpdateMeterStatusDTO;
import com.byy.meterreading.vo.meter.CreateMeterVO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meter.MeterDetailVO;
import com.byy.meterreading.vo.meter.MeterListItemVO;
import com.byy.meterreading.vo.meter.MeterVersionVO;

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

    /** 分页查询未删除的表具。 */
    PageVO<MeterListItemVO> listMeters(MeterPageQueryDTO queryDTO);

    /** 根据主键查询表具完整档案。 */
    MeterDetailVO getMeter(Long meterId);

    /** 修改表具基础资料并返回最新版本。 */
    MeterVersionVO updateMeter(
            Long operatorId,
            Long meterId,
            UpdateMeterDTO updateMeterDTO
    );

    /** 按生命周期规则修改表具状态并返回最新版本。 */
    MeterVersionVO updateMeterStatus(
            Long operatorId,
            Long meterId,
            UpdateMeterStatusDTO updateMeterStatusDTO
    );

    /** 按版本号逻辑删除停用状态的表具。 */
    void deleteMeter(Long operatorId, Long meterId, Integer version);
}
