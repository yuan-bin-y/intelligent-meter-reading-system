package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.mapper.projection.MeterBoundResidentRow;
import com.byy.meterreading.mapper.projection.ResidentBoundMeterRow;
import com.byy.meterreading.mapper.projection.ResidentMeterBindingRow;
import com.byy.meterreading.model.ResidentMeter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 居民表具关系 Mapper；简单写操作使用 MyBatis-Plus，关联查询使用 XML。
 */
@Mapper
public interface ResidentMeterMapper extends BaseMapper<ResidentMeter> {

    IPage<ResidentMeterBindingRow> selectBindingPage(
            Page<ResidentMeterBindingRow> page,
            @Param("residentId") Long residentId,
            @Param("meterId") Long meterId,
            @Param("keyword") String keyword
    );

    IPage<ResidentBoundMeterRow> selectResidentMeterPage(
            Page<ResidentBoundMeterRow> page,
            @Param("residentId") Long residentId,
            @Param("keyword") String keyword
    );

    IPage<MeterBoundResidentRow> selectMeterResidentPage(
            Page<MeterBoundResidentRow> page,
            @Param("meterId") Long meterId,
            @Param("keyword") String keyword
    );
}
