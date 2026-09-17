package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.Meter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表具档案 Mapper，简单单表操作使用 MyBatis-Plus 通用方法。
 */
@Mapper
public interface MeterMapper extends BaseMapper<Meter> {
}
