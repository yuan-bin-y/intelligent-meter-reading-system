package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.DeviceAlarm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备告警单表数据访问接口。
 */
@Mapper
public interface DeviceAlarmMapper extends BaseMapper<DeviceAlarm> {
}
