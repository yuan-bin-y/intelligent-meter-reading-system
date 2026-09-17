package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.Device;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备档案 Mapper，当前全部数据库操作使用 MyBatis-Plus。
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
}
