package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.MeterReadingResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抄表结果 Mapper；当前执行模块只需要单表写入能力。
 */
@Mapper
public interface MeterReadingResultMapper
        extends BaseMapper<MeterReadingResult> {
}
