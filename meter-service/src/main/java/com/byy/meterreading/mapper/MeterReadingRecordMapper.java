package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.mapper.projection.MeterReadingRecordRow;
import com.byy.meterreading.model.MeterReadingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 正式抄表记录写入和多表查询。 */
@Mapper
public interface MeterReadingRecordMapper
        extends BaseMapper<MeterReadingRecord> {

    IPage<MeterReadingRecordRow> selectRecordPage(
            Page<MeterReadingRecordRow> page,
            @Param("residentId") Long residentId,
            @Param("keyword") String keyword,
            @Param("meterId") Long meterId,
            @Param("sourceType") String sourceType,
            @Param("readingAtStart") LocalDateTime readingAtStart,
            @Param("readingAtEnd") LocalDateTime readingAtEnd
    );

    MeterReadingRecordRow selectRecordDetail(
            @Param("recordId") Long recordId,
            @Param("residentId") Long residentId
    );
}
