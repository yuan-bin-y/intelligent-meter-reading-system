package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.mapper.projection.DeviceAlarmRow;
import com.byy.meterreading.model.DeviceAlarm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 设备告警数据访问接口；告警状态写入使用 MyBatis-Plus，
 * 告警与设备的关联查询使用 XML。
 */
@Mapper
public interface DeviceAlarmMapper extends BaseMapper<DeviceAlarm> {

    IPage<DeviceAlarmRow> selectAlarmPage(
            Page<DeviceAlarmRow> page,
            @Param("deviceNo") String deviceNo,
            @Param("deviceName") String deviceName,
            @Param("alarmType") String alarmType,
            @Param("alarmStatus") String alarmStatus,
            @Param("occurredAtStart") LocalDateTime occurredAtStart,
            @Param("occurredAtEnd") LocalDateTime occurredAtEnd
    );

    DeviceAlarmRow selectAlarmById(@Param("alarmId") Long alarmId);
}
