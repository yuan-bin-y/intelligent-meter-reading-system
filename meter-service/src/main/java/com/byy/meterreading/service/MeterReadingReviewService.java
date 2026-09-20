package com.byy.meterreading.service;

import com.byy.meterreading.dto.meterreadingreview.ApproveMeterReadingResultDTO;
import com.byy.meterreading.dto.meterreadingreview.MeterReadingResultPageQueryDTO;
import com.byy.meterreading.dto.meterreadingreview.RejectMeterReadingResultDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingResultDetailVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingResultListItemVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingReviewDecisionVO;

/** 抄表结果查询、审核和抄表员查看本人结果。 */
public interface MeterReadingReviewService {
    PageVO<MeterReadingResultListItemVO> listResults(
            MeterReadingResultPageQueryDTO queryDTO
    );

    MeterReadingResultDetailVO getResult(Long resultId);

    MeterReadingReviewDecisionVO approve(
            Long reviewerId,
            Long resultId,
            ApproveMeterReadingResultDTO approveDTO
    );

    MeterReadingReviewDecisionVO reject(
            Long reviewerId,
            Long resultId,
            RejectMeterReadingResultDTO rejectDTO
    );

    MeterReadingResultDetailVO getLatestReaderResult(
            Long meterReaderId,
            Long taskId
    );
}
