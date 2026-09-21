package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.mapper.projection.MeterReadingReviewHistoryRow;
import com.byy.meterreading.model.MeterReadingReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 审核历史单表写入和任务历史关联查询。 */
@Mapper
public interface MeterReadingReviewMapper
        extends BaseMapper<MeterReadingReview> {

    List<MeterReadingReviewHistoryRow> selectTaskReviewHistory(
            @Param("taskId") Long taskId
    );

    /** 查询指定提交结果自身的审核记录。 */
    List<MeterReadingReviewHistoryRow> selectResultReviewHistory(
            @Param("resultId") Long resultId
    );
}
