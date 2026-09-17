package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.mapper.projection.DeviceBoundMeterRow;
import com.byy.meterreading.mapper.projection.DeviceMeterBindingRow;
import com.byy.meterreading.mapper.projection.MeterBoundDeviceRow;
import com.byy.meterreading.model.DeviceMeter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 设备表具关系 Mapper；简单写操作使用 MyBatis-Plus，关联查询使用 XML。
 */
@Mapper
public interface DeviceMeterMapper extends BaseMapper<DeviceMeter> {

    IPage<DeviceMeterBindingRow> selectBindingPage(
            Page<DeviceMeterBindingRow> page,
            @Param("deviceId") Long deviceId,
            @Param("meterId") Long meterId,
            @Param("keyword") String keyword
    );

    IPage<DeviceBoundMeterRow> selectDeviceMeterPage(
            Page<DeviceBoundMeterRow> page,
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword
    );

    IPage<MeterBoundDeviceRow> selectMeterDevicePage(
            Page<MeterBoundDeviceRow> page,
            @Param("meterId") Long meterId,
            @Param("keyword") String keyword
    );
}
