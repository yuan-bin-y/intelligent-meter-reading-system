package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.MeterReadingResultImage;
import org.apache.ibatis.annotations.Mapper;

/** 抄表结果与图片关联 Mapper。 */
@Mapper
public interface MeterReadingResultImageMapper
        extends BaseMapper<MeterReadingResultImage> {
}
