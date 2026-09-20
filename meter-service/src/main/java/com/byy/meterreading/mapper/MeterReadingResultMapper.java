package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.mapper.projection.MeterReadingResultRow;
import com.byy.meterreading.model.MeterReadingResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 抄表结果 Mapper；当前执行模块只需要单表写入能力。
 */
@Mapper
public interface MeterReadingResultMapper
        extends BaseMapper<MeterReadingResult> {

    IPage<MeterReadingResultRow> selectResultPage(
            Page<MeterReadingResultRow> page,
            @Param("keyword") String keyword,
            @Param("sourceType") String sourceType,
            @Param("reviewStatus") String reviewStatus,
            @Param("submittedAtStart") LocalDateTime submittedAtStart,
            @Param("submittedAtEnd") LocalDateTime submittedAtEnd
    );

    MeterReadingResultRow selectResultDetail(
            @Param("resultId") Long resultId
    );

    MeterReadingResult selectByIdForUpdate(@Param("resultId") Long resultId);

    Integer selectNextAttemptNo(@Param("taskId") Long taskId);

    Long selectLatestReaderResultId(
            @Param("taskId") Long taskId,
            @Param("meterReaderId") Long meterReaderId
    );
}
